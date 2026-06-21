package com.novage.p2pml.internal.parser

import com.novage.p2pml.api.events.Segment
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMediaPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMultivariantPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsSegment
import com.novage.p2pml.internal.parser.hlsPlaylistParser.Rendition
import com.novage.p2pml.internal.parser.hlsPlaylistParser.Stream
import com.novage.p2pml.internal.parser.hlsPlaylistParser.UpdateStreamParams
import com.novage.p2pml.internal.parser.hlsPlaylistParser.extractVideoCodec
import com.novage.p2pml.internal.utils.Clock
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.utils.SystemClock
import kotlin.time.Duration.Companion.seconds

private const val MAIN_STREAM = "main"
private const val SECONDARY_STREAM = "secondary"
private const val MICROSECONDS_IN_SECOND = 1_000_000.0
private val LIVE_VARIANT_TTL = 60.seconds

internal class HlsStreamStateTracker(
    private val clock: Clock = SystemClock,
    private val logger: CoreLogger = CoreLogger("HlsStreamStateTracker")
) {

    private var currentMasterManifestUrl: String? = null

    private val trackedStreams = mutableMapOf<String, TrackedStreamContext>()

    private val runtimeIdToSegmentMap = mutableMapOf<String, Pair<String, Segment>>()

    fun isCurrentSegment(segmentUrl: String): Boolean =
        trackedStreams.values.any { it.currentSegmentRuntimeIds.contains(segmentUrl) }

    fun isManifestTracked(manifestUrl: String): Boolean =
        currentMasterManifestUrl == manifestUrl || trackedStreams.containsKey(manifestUrl)

    fun getUpdateStreamParams(variantUrl: String): UpdateStreamParams? = trackedStreams[variantUrl]?.updateParams

    fun getStreams(): List<Stream> = trackedStreams.values.map { it.stream }

    fun getSegmentWithManifestByUrl(runtimeId: String): Pair<String, Segment>? = runtimeIdToSegmentMap[runtimeId]

    fun reset() {
        logger.i { "Resetting tracker state." }
        trackedStreams.clear()
        runtimeIdToSegmentMap.clear()
        currentMasterManifestUrl = null
    }

    fun postProcessMultivariantPlaylist(manifestUrl: String, hlsPlaylist: HlsMultivariantPlaylist) {
        currentMasterManifestUrl = manifestUrl

        hlsPlaylist.variants.forEach { variant ->
            if (!variant.isIFrame) {
                getOrCreateContext(variant.url.absolute) {
                    Stream(
                        runtimeId = variant.url.absolute,
                        type = MAIN_STREAM,
                        bitrate = variant.bandwidth ?: variant.averageBandwidth,
                        codecs = extractVideoCodec(variant.codecs),
                        width = variant.width,
                        height = variant.height,
                        frameRate = variant.frameRate,
                        videoRange = variant.videoRange
                    )
                }
            }
        }
        addRenditionStreams(hlsPlaylist.videos, MAIN_STREAM)
        addRenditionStreams(hlsPlaylist.audios, SECONDARY_STREAM)
    }

    private fun addRenditionStreams(renditions: List<Rendition>, streamType: String) {
        renditions.forEach { rendition ->
            rendition.url?.let { url ->
                getOrCreateContext(url.absolute) {
                    Stream(
                        runtimeId = url.absolute,
                        type = streamType,
                        bitrate = 0,
                        language = rendition.language,
                        channels = rendition.channels,
                        name = rendition.name
                    )
                }
            }
        }
    }

    fun postProcessMediaPlaylist(manifestUrl: String, mediaPlaylist: HlsMediaPlaylist) {
        val mediaType = trackedStreams[manifestUrl]?.stream?.type ?: MAIN_STREAM
        val isStreamLive = !mediaPlaylist.hasEndTag
        val newMediaSequence = mediaPlaylist.mediaSequence

        val initialStartTime = calculateInitialStartTime(isStreamLive, mediaPlaylist.hlsSegments)

        val context = getOrCreateContext(manifestUrl) {
            Stream(runtimeId = manifestUrl, type = mediaType, bitrate = 0)
        }

        context.currentSegmentRuntimeIds.clear()

        if (isStreamLive) {
            context.lastUpdated = clock.timeSource.markNow()
        }

        var segmentIndex = if (isStreamLive) newMediaSequence else 0
        val segmentsToAdd = mutableListOf<Segment>()

        mediaPlaylist.hlsSegments.forEach { segment ->
            context.currentSegmentRuntimeIds.add(segment.runtimeUrl)
            val newSegment =
                createAndStoreSegment(manifestUrl, context.segments, segmentIndex++, initialStartTime, segment)
            newSegment?.let { segmentsToAdd.add(it) }
        }

        val segmentsToRemove = enforceLiveTtlAndGetObsoleteSegments(manifestUrl, newMediaSequence, isStreamLive)

        context.updateParams = UpdateStreamParams(
            streamRuntimeId = manifestUrl,
            addSegments = segmentsToAdd,
            removeSegmentsIds = segmentsToRemove,
            isLive = isStreamLive
        )

        logger.d { "Segments updated. Added: ${segmentsToAdd.size}, Removed: ${segmentsToRemove.size}" }
    }

    private fun createAndStoreSegment(
        manifestUrl: String,
        segmentsMap: MutableMap<Long, Segment>,
        segmentId: Long,
        initialStartTime: Double,
        hlsSegment: HlsSegment
    ): Segment? {
        if (segmentsMap.contains(segmentId)) return null

        val startTime = if (hlsSegment.programDateTimeUs != null) {
            hlsSegment.programDateTimeUs / MICROSECONDS_IN_SECOND
        } else {
            segmentsMap[segmentId - 1]?.endTime ?: initialStartTime
        }
        val endTime = startTime + (hlsSegment.durationUs / MICROSECONDS_IN_SECOND)

        return Segment(
            runtimeId = hlsSegment.runtimeUrl,
            externalId = segmentId,
            url = hlsSegment.url.absolute,
            byteRange = hlsSegment.byteRange,
            startTime = startTime,
            endTime = endTime
        ).also {
            segmentsMap[segmentId] = it
            runtimeIdToSegmentMap[it.runtimeId] = manifestUrl to it
        }
    }

    private fun enforceLiveTtlAndGetObsoleteSegments(
        variantUrl: String,
        removeUntilId: Long,
        isLive: Boolean
    ): List<String> {
        val obsoleteSegmentIds = mutableListOf<String>()

        if (removeUntilId > 0) {
            collectObsoleteSegments(variantUrl, removeUntilId, obsoleteSegmentIds)
        }

        if (isLive) {
            evictAbandonedLiveVariants(variantUrl)
        }

        return obsoleteSegmentIds
    }

    private fun collectObsoleteSegments(
        variantUrl: String,
        removeUntilId: Long,
        obsoleteSegmentIds: MutableList<String>
    ) {
        val segmentsMap = trackedStreams[variantUrl]?.segments ?: return

        segmentsMap.entries.removeAll { (key, segment) ->
            val isObsolete = key < removeUntilId
            if (isObsolete) {
                val runtimeId = segment.runtimeId
                obsoleteSegmentIds.add(runtimeId)
                runtimeIdToSegmentMap.remove(runtimeId)
            }
            isObsolete
        }
    }

    private fun evictAbandonedLiveVariants(activeVariantUrl: String) {
        trackedStreams.entries.removeAll { (staleUrl, context) ->
            val isAbandoned = staleUrl != activeVariantUrl && context.lastUpdated.elapsedNow() > LIVE_VARIANT_TTL
            if (isAbandoned) {
                logger.d { "Evicting abandoned live variant from parser memory: $staleUrl" }
                context.segments.values.forEach {
                    runtimeIdToSegmentMap.remove(it.runtimeId)
                }
            }
            isAbandoned
        }
    }

    private fun calculateInitialStartTime(isLive: Boolean, segments: List<HlsSegment>): Double {
        if (!isLive || segments.isEmpty()) return 0.0

        val anchorIndex = segments.indexOfFirst { it.programDateTimeUs != null }
        if (anchorIndex != -1) {
            val anchorTimeUs = segments[anchorIndex].programDateTimeUs!!
            val precedingDurationUs = segments.subList(0, anchorIndex).sumOf { it.durationUs }
            return (anchorTimeUs - precedingDurationUs) / MICROSECONDS_IN_SECOND
        }

        val totalDurationSec = segments.sumOf { it.durationUs / MICROSECONDS_IN_SECOND }
        return getCurrentEpochSeconds() - totalDurationSec
    }

    private fun getCurrentEpochSeconds(): Double = clock.getCurrentEpochSeconds()

    private inline fun getOrCreateContext(manifestUrl: String, streamFactory: () -> Stream): TrackedStreamContext =
        trackedStreams.getOrPut(manifestUrl) {
            TrackedStreamContext(
                stream = streamFactory(),
                lastUpdated = clock.timeSource.markNow()
            )
        }
}
