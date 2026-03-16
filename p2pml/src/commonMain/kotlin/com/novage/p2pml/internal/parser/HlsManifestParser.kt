package com.novage.p2pml.internal.parser

import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaylistSnapshot
import com.novage.p2pml.api.models.Segment
import com.novage.p2pml.internal.parser.encoding.encodeToUrlSafeBase64
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMediaPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMultivariantPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsPlaylistParser
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsSegment
import com.novage.p2pml.internal.parser.hlsPlaylistParser.InitializationSegment
import com.novage.p2pml.internal.parser.hlsPlaylistParser.Rendition
import com.novage.p2pml.internal.parser.hlsPlaylistParser.Stream
import com.novage.p2pml.internal.parser.hlsPlaylistParser.UpdateStreamParams
import com.novage.p2pml.internal.server.config.LocalUrlFactory
import com.novage.p2pml.internal.utils.CoreLogger
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val MAIN_STREAM = "main"
private const val SECONDARY_STREAM = "secondary"
private const val MICROSECONDS_IN_SECOND = 1_000_000.0
private val LIVE_VARIANT_TTL = 60.seconds

internal class HlsManifestParser(
    private val playbackProvider: PlaybackProvider,
    private val urlFactory: LocalUrlFactory
) {
    private val logger = CoreLogger("HlsManifestParser")
    private val parser = HlsPlaylistParser()
    private val mutex = Mutex()

    private var currentMasterManifestUrl: String? = null
    private val streams = mutableMapOf<String, Stream>()
    private val streamSegments = mutableMapOf<String, MutableMap<Long, Segment>>()
    private val updateStreamParams = mutableMapOf<String, UpdateStreamParams>()

    private val currentSegmentRuntimeIds = mutableMapOf<String, MutableSet<String>>()
    private val variantLastUpdated = mutableMapOf<String, TimeMark>()

    suspend fun getModifiedManifest(originalManifest: String, manifestUrl: String): String = mutex.withLock {
        logger.d { "Processing manifest: $manifestUrl (Length: ${originalManifest.length})" }
        parseHlsManifest(manifestUrl, originalManifest)
    }

    private fun addCurrentSegmentRuntimeId(manifestUrl: String, runtimeId: String, isLive: Boolean) {
        currentSegmentRuntimeIds.getOrPut(manifestUrl) { mutableSetOf() }.add(runtimeId)

        if (isLive) {
            variantLastUpdated[manifestUrl] = TimeSource.Monotonic.markNow()
        }
    }

    private fun clearCurrentSegmentRuntimeIds(manifestUrl: String) {
        currentSegmentRuntimeIds[manifestUrl]?.clear()
    }

    private fun clearAllCurrentSegmentRuntimeIds() {
        currentSegmentRuntimeIds.clear()
        variantLastUpdated.clear()
    }

    suspend fun isCurrentSegment(segmentUrl: String): Boolean = mutex.withLock {
        currentSegmentRuntimeIds.values.any { it.contains(segmentUrl) }
    }

    suspend fun isManifestTracked(manifestUrl: String): Boolean = mutex.withLock {
        currentMasterManifestUrl == manifestUrl || streams.containsKey(manifestUrl)
    }

    private suspend fun parseHlsManifest(manifestUrl: String, manifest: String): String =
        when (val hlsPlaylist = parser.parse(manifestUrl, manifest)) {
            is HlsMediaPlaylist -> {
                logger.d { "Type: Media Playlist. Live: ${!hlsPlaylist.hasEndTag}" }
                parseMediaPlaylist(manifestUrl, hlsPlaylist, manifest)
            }

            is HlsMultivariantPlaylist -> {
                logger.d { "Type: Multivariant (Master) Playlist" }
                parseMultivariantPlaylist(manifestUrl, hlsPlaylist, manifest)
            }
        }

    private fun parseMultivariantPlaylist(
        manifestUrl: String,
        hlsPlaylist: HlsMultivariantPlaylist,
        originalManifest: String
    ): String {
        val builder = StringBuilder(originalManifest)
        currentMasterManifestUrl = manifestUrl

        hlsPlaylist.variants.forEach { variant ->
            if (variant.isIFrame) {
                replaceUrlInManifest(builder, variant.url.original, variant.url.absolute)
            } else {
                processStream(variant.url.absolute, variant.url.original, MAIN_STREAM, builder)
            }
        }

        processMediaRenditions(hlsPlaylist.videos, MAIN_STREAM, builder)
        processMediaRenditions(hlsPlaylist.audios, SECONDARY_STREAM, builder)

        replaceTextTrackUrls(hlsPlaylist.subtitles, builder)
        replaceTextTrackUrls(hlsPlaylist.closedCaptions, builder)
        hlsPlaylist.sessionKeyUrls.forEach {
            replaceUrlInManifest(builder, it.original, it.absolute)
        }

        return builder.toString()
    }

    private fun processMediaRenditions(renditions: List<Rendition>, streamType: String, builder: StringBuilder) {
        renditions.forEach { rendition ->
            val parsedUrl = rendition.url ?: return@forEach
            processStream(parsedUrl.absolute, parsedUrl.original, streamType, builder)
        }
    }

    private fun replaceTextTrackUrls(renditions: List<Rendition>, builder: StringBuilder) {
        renditions.forEach { rendition ->
            val parsedUrl = rendition.url ?: return@forEach
            replaceUrlInManifest(builder, parsedUrl.original, parsedUrl.absolute)
        }
    }

    private fun replaceLowLatencyUrls(mediaPlaylist: HlsMediaPlaylist, builder: StringBuilder) {
        mediaPlaylist.parts.forEach { replaceUrlInManifest(builder, it.original, it.absolute) }
        mediaPlaylist.preloadHints.forEach { replaceUrlInManifest(builder, it.original, it.absolute) }
        mediaPlaylist.renditionReports.forEach { replaceUrlInManifest(builder, it.original, it.absolute) }
    }

    private suspend fun parseMediaPlaylist(
        manifestUrl: String,
        mediaPlaylist: HlsMediaPlaylist,
        originalManifest: String
    ): String {
        val mediaType = streams[manifestUrl]?.type ?: MAIN_STREAM
        val isStreamLive = !mediaPlaylist.hasEndTag
        val newMediaSequence = mediaPlaylist.mediaSequence
        val builder = StringBuilder(originalManifest)

        val segmentsToRemove = removeObsoleteSegments(manifestUrl, newMediaSequence, isStreamLive)
        val segmentsToAdd = mutableListOf<Segment>()

        val initialStartTime = getInitialStartTime(isStreamLive, mediaPlaylist)

        clearCurrentSegmentRuntimeIds(manifestUrl)

        replaceLowLatencyUrls(mediaPlaylist, builder)

        var searchOffset = 0
        var lastProcessedInitSegment: InitializationSegment? = null
        var segmentIndex = if (isStreamLive) newMediaSequence else 0

        mediaPlaylist.hlsSegments.forEach { segment ->
            segment.encryptionKey?.let { key ->
                searchOffset = replaceUrlInManifest(
                    builder,
                    key.original,
                    key.absolute,
                    searchOffset
                )
            }

            if (segment.initializationSegment != null && segment.initializationSegment !== lastProcessedInitSegment) {
                val initSeg = segment.initializationSegment
                val encodedInitUrl = encodeToUrlSafeBase64(initSeg.url.absolute)
                val newInitUrl = urlFactory.buildSegmentUrl(encodedInitUrl)

                searchOffset = replaceUrlInManifest(
                    builder,
                    initSeg.url.original,
                    newInitUrl,
                    searchOffset
                )
                lastProcessedInitSegment = initSeg
            }

            addCurrentSegmentRuntimeId(manifestUrl, segment.runtimeUrl, isStreamLive)

            val encodedSegmentUrl = segment.byteRange?.let {
                encodeToUrlSafeBase64("${segment.url.absolute}|${it.start}-${it.end}")
            } ?: encodeToUrlSafeBase64(segment.url.absolute)

            val newSegmentUrl = urlFactory.buildSegmentUrl(encodedSegmentUrl)
            searchOffset = replaceUrlInManifest(
                builder,
                segment.url.original,
                newSegmentUrl,
                searchOffset
            )

            val newSegment = addNewSegment(manifestUrl, segmentIndex++, initialStartTime, segment)
            if (newSegment != null) segmentsToAdd.add(newSegment)
        }

        updateStreamData(manifestUrl, segmentsToAdd, segmentsToRemove, isStreamLive)

        if (!streams.containsKey(manifestUrl)) {
            streams[manifestUrl] = Stream(
                runtimeId = manifestUrl,
                type = mediaType,
                index = streams.values.count { it.type == mediaType }
            )
        }

        logger.d { "Segments updated. Added: ${segmentsToAdd.size}, Removed: ${segmentsToRemove.size}" }

        return builder.toString()
    }

    private fun updateStreamData(
        variantUrl: String,
        newSegments: List<Segment>,
        segmentsToRemove: List<String>,
        isLive: Boolean
    ) {
        val updateStream = UpdateStreamParams(
            streamRuntimeId = variantUrl,
            addSegments = newSegments,
            removeSegmentsIds = segmentsToRemove,
            isLive = isLive
        )
        updateStreamParams[variantUrl] = updateStream
    }

    private fun addNewSegment(
        variantUrl: String,
        segmentId: Long,
        initialStartTime: Double,
        hlsSegment: HlsSegment
    ): Segment? {
        val segmentsMap = streamSegments.getOrPut(variantUrl) { mutableMapOf() }
        if (segmentsMap.contains(segmentId)) return null

        val prevSegment = segmentsMap[segmentId - 1]
        val segmentDurationInSeconds = hlsSegment.durationUs / MICROSECONDS_IN_SECOND

        val startTime = prevSegment?.endTime ?: initialStartTime
        val endTime = startTime + segmentDurationInSeconds

        val newSegment = Segment(
            runtimeId = hlsSegment.runtimeUrl,
            externalId = segmentId,
            url = hlsSegment.url.absolute,
            byteRange = hlsSegment.byteRange,
            startTime = startTime,
            endTime = endTime
        )

        segmentsMap[segmentId] = newSegment
        return newSegment
    }

    private fun processStream(
        absoluteStreamUrl: String,
        streamUrlInManifest: String,
        streamType: String,
        updatedManifestBuilder: StringBuilder
    ) {
        if (!streams.containsKey(absoluteStreamUrl)) {
            val nextIndex = streams.values.count { it.type == streamType }
            streams[absoluteStreamUrl] = Stream(runtimeId = absoluteStreamUrl, type = streamType, index = nextIndex)
        }

        val encodedUrl = absoluteStreamUrl.encodeURLParameter()
        val newUrl = urlFactory.buildManifestUrl(encodedUrl)

        replaceUrlInManifest(updatedManifestBuilder, streamUrlInManifest, newUrl)
    }

    private fun replaceUrlInManifest(
        updatedManifestBuilder: StringBuilder,
        oldUrl: String,
        newUrl: String,
        startIndex: Int = 0
    ): Int {
        val foundIndex = updatedManifestBuilder.indexOf(oldUrl, startIndex)
        if (foundIndex == -1) {
            logger.w { "Failed to rewrite URL. Original string not found after index $startIndex: $oldUrl" }
            return startIndex
        }

        val endIndex = foundIndex + oldUrl.length
        updatedManifestBuilder.setRange(foundIndex, endIndex, newUrl)

        return foundIndex + newUrl.length
    }

    private fun removeObsoleteSegments(variantUrl: String, removeUntilId: Long, isLive: Boolean): List<String> {
        val obsoleteSegmentIds = mutableListOf<String>()

        val segmentsMap = streamSegments[variantUrl]
        if (segmentsMap != null) {
            val obsoleteSegments = segmentsMap.filterKeys { it < removeUntilId }
            obsoleteSegments.forEach { (id, _) -> segmentsMap.remove(id) }
            obsoleteSegmentIds.addAll(obsoleteSegments.values.map { it.runtimeId })
        }

        if (isLive) {
            val staleManifests = variantLastUpdated.filterValues {
                it.elapsedNow() > LIVE_VARIANT_TTL
            }.keys.toSet()

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

    private suspend fun getInitialStartTime(isLive: Boolean, mediaPlaylist: HlsMediaPlaylist): Double {
        if (isLive) {
            val snapshot = PlaylistSnapshot(
                mediaSequence = mediaPlaylist.mediaSequence,
                hasEndTag = mediaPlaylist.hasEndTag,
                segmentDurationsSec = mediaPlaylist.hlsSegments.map {
                    it.durationUs / MICROSECONDS_IN_SECOND
                }
            )
            return playbackProvider.getAbsolutePlaybackPosition(snapshot)
        } else {
            return 0.0
        }
    }

    suspend fun getUpdateStreamParamsJson(variantUrl: String): String? = mutex.withLock {
        val params = updateStreamParams[variantUrl] ?: return@withLock null
        Json.encodeToString(params)
    }

    suspend fun getStreamsJson(): String = mutex.withLock {
        Json.encodeToString(streams.values.toList())
    }

    suspend fun reset() = mutex.withLock {
        logger.i { "Resetting parser state." }
        streams.clear()
        streamSegments.clear()
        updateStreamParams.clear()
        clearAllCurrentSegmentRuntimeIds()
    }
}
