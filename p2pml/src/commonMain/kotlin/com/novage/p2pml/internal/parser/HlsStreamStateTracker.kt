package com.novage.p2pml.internal.parser

import com.novage.p2pml.api.models.Segment
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMediaPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMultivariantPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsSegment
import com.novage.p2pml.internal.parser.hlsPlaylistParser.Rendition
import com.novage.p2pml.internal.parser.hlsPlaylistParser.Stream
import com.novage.p2pml.internal.parser.hlsPlaylistParser.UpdateStreamParams
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.utils.extractVideoCodec
import com.novage.p2pml.internal.utils.getCurrentEpochSeconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val MAIN_STREAM = "main"
private const val SECONDARY_STREAM = "secondary"
private const val MICROSECONDS_IN_SECOND = 1_000_000.0
private val LIVE_VARIANT_TTL = 60.seconds

internal class HlsStreamStateTracker {
    private val logger = CoreLogger("HlsStreamStateTracker")

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

        val segmentsToRemove = enforceLiveTtlAndGetObsoleteSegments(manifestUrl, newMediaSequence, isStreamLive)
        val segmentsToAdd = mutableListOf<Segment>()

        val initialStartTime = calculateInitialStartTime(isStreamLive)

        val context = getOrCreateContext(manifestUrl) {
            Stream(runtimeId = manifestUrl, type = mediaType, bitrate = 0)
        }

        context.currentSegmentRuntimeIds.clear()

        if (isStreamLive) {
            context.lastUpdated = TimeSource.Monotonic.markNow()
        }

        var segmentIndex = if (isStreamLive) newMediaSequence else 0

        mediaPlaylist.hlsSegments.forEach { segment ->
            context.currentSegmentRuntimeIds.add(segment.runtimeUrl)
            val newSegment =
                createAndStoreSegment(manifestUrl, context.segments, segmentIndex++, initialStartTime, segment)
            newSegment?.let { segmentsToAdd.add(it) }
        }

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

        val startTime = segmentsMap[segmentId - 1]?.endTime ?: initialStartTime
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

        val iterator = segmentsMap.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key < removeUntilId) {
                val runtimeId = entry.value.runtimeId
                obsoleteSegmentIds.add(runtimeId)
                runtimeIdToSegmentMap.remove(runtimeId)
                iterator.remove()
            }
        }
    }

    private fun evictAbandonedLiveVariants(activeVariantUrl: String) {
        val iterator = trackedStreams.entries.iterator()

        while (iterator.hasNext()) {
            val (staleUrl, context) = iterator.next()
            if (staleUrl != activeVariantUrl && context.lastUpdated.elapsedNow() > LIVE_VARIANT_TTL) {
                logger.d { "Evicting abandoned live variant from parser memory: $staleUrl" }

                context.segments.values.forEach {
                    runtimeIdToSegmentMap.remove(it.runtimeId)
                }

                iterator.remove()
            }
        }
    }

    private fun calculateInitialStartTime(isLive: Boolean): Double = if (isLive) getCurrentEpochSeconds() else 0.0

    private inline fun getOrCreateContext(manifestUrl: String, streamFactory: () -> Stream): TrackedStreamContext =
        trackedStreams.getOrPut(manifestUrl) {
            TrackedStreamContext(stream = streamFactory())
        }
}
