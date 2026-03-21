package com.novage.p2pml.internal.parser

import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaylistSnapshot
import com.novage.p2pml.api.models.Segment
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMediaPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMultivariantPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsSegment
import com.novage.p2pml.internal.parser.hlsPlaylistParser.Stream
import com.novage.p2pml.internal.parser.hlsPlaylistParser.UpdateStreamParams
import com.novage.p2pml.internal.utils.CoreLogger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val MAIN_STREAM = "main"
private const val SECONDARY_STREAM = "secondary"
private const val MICROSECONDS_IN_SECOND = 1_000_000.0
private val LIVE_VARIANT_TTL = 60.seconds

internal class HlsStreamStateTracker(
    private val playbackProvider: PlaybackProvider
) {
    private val logger = CoreLogger("HlsStreamStateTracker")

    private var currentMasterManifestUrl: String? = null
    private val streams = mutableMapOf<String, Stream>()
    private val streamSegments = mutableMapOf<String, MutableMap<Long, Segment>>()
    private val updateStreamParams = mutableMapOf<String, UpdateStreamParams>()

    private val currentSegmentRuntimeIds = mutableMapOf<String, MutableSet<String>>()
    private val variantLastUpdated = mutableMapOf<String, TimeMark>()

    fun isCurrentSegment(segmentUrl: String): Boolean = 
        currentSegmentRuntimeIds.values.any { it.contains(segmentUrl) }

    fun isManifestTracked(manifestUrl: String): Boolean = 
        currentMasterManifestUrl == manifestUrl || streams.containsKey(manifestUrl)

    fun getUpdateStreamParams(variantUrl: String): UpdateStreamParams? = 
        updateStreamParams[variantUrl]

    fun getStreams(): List<Stream> = streams.values.toList()

    fun reset() {
        logger.i { "Resetting tracker state." }
        streams.clear()
        streamSegments.clear()
        updateStreamParams.clear()
        currentSegmentRuntimeIds.clear()
        variantLastUpdated.clear()
        currentMasterManifestUrl = null
    }

    fun postProcessMultivariantPlaylist(manifestUrl: String, hlsPlaylist: HlsMultivariantPlaylist) {
        currentMasterManifestUrl = manifestUrl

        hlsPlaylist.variants.forEach { variant ->
            if (!variant.isIFrame) addStreamIfAbsent(variant.url.absolute, MAIN_STREAM)
        }
        hlsPlaylist.videos.forEach { rendition ->
            rendition.url?.let { addStreamIfAbsent(it.absolute, MAIN_STREAM) }
        }
        hlsPlaylist.audios.forEach { rendition ->
            rendition.url?.let { addStreamIfAbsent(it.absolute, SECONDARY_STREAM) }
        }
    }

    private fun addStreamIfAbsent(absoluteStreamUrl: String, streamType: String) {
        if (!streams.containsKey(absoluteStreamUrl)) {
            val nextIndex = streams.values.count { it.type == streamType }
            streams[absoluteStreamUrl] = Stream(runtimeId = absoluteStreamUrl, type = streamType, index = nextIndex)
        }
    }

    suspend fun postProcessMediaPlaylist(manifestUrl: String, mediaPlaylist: HlsMediaPlaylist) {
        val mediaType = streams[manifestUrl]?.type ?: MAIN_STREAM
        val isStreamLive = !mediaPlaylist.hasEndTag
        val newMediaSequence = mediaPlaylist.mediaSequence

        val segmentsToRemove = enforceLiveTtlAndGetObsoleteSegments(manifestUrl, newMediaSequence, isStreamLive)
        val segmentsToAdd = mutableListOf<Segment>()

        val initialStartTime = calculateInitialStartTime(isStreamLive, mediaPlaylist)

        currentSegmentRuntimeIds[manifestUrl]?.clear()

        var segmentIndex = if (isStreamLive) newMediaSequence else 0

        mediaPlaylist.hlsSegments.forEach { segment ->
            trackLiveSegment(manifestUrl, segment.runtimeUrl, isStreamLive)
            val newSegment = createAndStoreSegment(manifestUrl, segmentIndex++, initialStartTime, segment)
            newSegment?.let { segmentsToAdd.add(it) }
        }

        updateStreamParams[manifestUrl] = UpdateStreamParams(
            streamRuntimeId = manifestUrl,
            addSegments = segmentsToAdd,
            removeSegmentsIds = segmentsToRemove,
            isLive = isStreamLive
        )

        addStreamIfAbsent(manifestUrl, mediaType)

        logger.d { "Segments updated. Added: ${segmentsToAdd.size}, Removed: ${segmentsToRemove.size}" }
    }

    private fun trackLiveSegment(manifestUrl: String, runtimeId: String, isLive: Boolean) {
        currentSegmentRuntimeIds.getOrPut(manifestUrl) { mutableSetOf() }.add(runtimeId)
        if (isLive) {
            variantLastUpdated[manifestUrl] = TimeSource.Monotonic.markNow()
        }
    }

    private fun createAndStoreSegment(
        variantUrl: String,
        segmentId: Long,
        initialStartTime: Double,
        hlsSegment: HlsSegment
    ): Segment? {
        val segmentsMap = streamSegments.getOrPut(variantUrl) { mutableMapOf() }
        if (segmentsMap.contains(segmentId)) return null

        val startTime = segmentsMap[segmentId - 1]?.endTime ?: initialStartTime
        val endTime = startTime + (hlsSegment.durationUs / MICROSECONDS_IN_SECOND)

        return Segment(
            runtimeId = hlsSegment.runtimeUrl,
            externalId = segmentId,
            url = hlsSegment.url.absolute,
            byteRange = hlsSegment.byteRange,
            startTime = startTime,
            endTime = endTime
        ).also { segmentsMap[segmentId] = it }
    }

    private fun enforceLiveTtlAndGetObsoleteSegments(variantUrl: String, removeUntilId: Long, isLive: Boolean): List<String> {
        val obsoleteSegmentIds = mutableListOf<String>()

        streamSegments[variantUrl]?.let { segmentsMap ->
            val obsoleteSegments = segmentsMap.filterKeys { it < removeUntilId }
            obsoleteSegments.keys.forEach { segmentsMap.remove(it) }
            obsoleteSegmentIds.addAll(obsoleteSegments.values.map { it.runtimeId })
        }

        if (isLive) {
            val staleManifests = variantLastUpdated.filterValues { it.elapsedNow() > LIVE_VARIANT_TTL }.keys.toSet()
            staleManifests.forEach { staleUrl ->
                if (staleUrl != variantUrl) {
                    logger.d { "Evicting abandoned live variant from parser memory: $staleUrl" }
                    currentSegmentRuntimeIds.remove(staleUrl)
                    variantLastUpdated.remove(staleUrl)
                    streamSegments.remove(staleUrl)
                }
            }
        }

        return obsoleteSegmentIds
    }

    private suspend fun calculateInitialStartTime(isLive: Boolean, mediaPlaylist: HlsMediaPlaylist): Double {
        if (!isLive) return 0.0

        val snapshot = PlaylistSnapshot(
            mediaSequence = mediaPlaylist.mediaSequence,
            hasEndTag = mediaPlaylist.hasEndTag,
            segmentDurationsSec = mediaPlaylist.hlsSegments.map { it.durationUs / MICROSECONDS_IN_SECOND }
        )
        return playbackProvider.getAbsolutePlaybackPosition(snapshot)
    }
}
