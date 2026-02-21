package com.novage.p2pml.internal.parser

import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaylistSnapshot
import com.novage.p2pml.api.models.Segment
import com.novage.p2pml.internal.parser.encoding.encodeUrlToBase64
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMediaPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMultivariantPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsPlaylistParser
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsSegment
import com.novage.p2pml.internal.parser.hlsPlaylistParser.InitializationSegment
import com.novage.p2pml.internal.parser.hlsPlaylistParser.Stream
import com.novage.p2pml.internal.parser.hlsPlaylistParser.UpdateStreamParams
import com.novage.p2pml.internal.server.config.LocalUrlFactory
import com.novage.p2pml.internal.utils.CoreLogger
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private const val MAIN_STREAM = "main"
private const val SECONDARY_STREAM = "secondary"
private const val MICROSECONDS_IN_SECOND = 1_000_000.0

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
    private val currentVideoSegmentRuntimeIds = mutableSetOf<String>()
    private val currentAudioSegmentRuntimeIds = mutableSetOf<String>()

    suspend fun getModifiedManifest(originalManifest: String, manifestUrl: String): String = mutex.withLock {
        logger.d { "Processing manifest: $manifestUrl (Length: ${originalManifest.length})" }
        parseHlsManifest(manifestUrl, originalManifest)
    }

    private fun addCurrentSegmentRuntimeId(streamType: String, runtimeId: String) {
        when (streamType) {
            MAIN_STREAM -> currentVideoSegmentRuntimeIds.add(runtimeId)
            SECONDARY_STREAM -> currentAudioSegmentRuntimeIds.add(runtimeId)
        }
    }

    private fun clearCurrentSegmentRuntimeIds(streamType: String) {
        when (streamType) {
            MAIN_STREAM -> currentVideoSegmentRuntimeIds.clear()
            SECONDARY_STREAM -> currentAudioSegmentRuntimeIds.clear()
        }
    }

    private fun clearCurrentSegmentRuntimeIds() {
        currentVideoSegmentRuntimeIds.clear()
        currentAudioSegmentRuntimeIds.clear()
    }

    suspend fun isCurrentSegment(segmentUrl: String): Boolean = mutex.withLock {
        currentVideoSegmentRuntimeIds.contains(segmentUrl) ||
            currentAudioSegmentRuntimeIds.contains(segmentUrl)
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

            else -> {
                logger.e { "Unsupported playlist type found for: $manifestUrl" }
                error("Unsupported playlist type")
            }
        }

    private fun parseMultivariantPlaylist(
        manifestUrl: String,
        hlsPlaylist: HlsMultivariantPlaylist,
        originalManifest: String
    ): String {
        val updatedManifestBuilder = StringBuilder(originalManifest)
        currentMasterManifestUrl = manifestUrl

        hlsPlaylist.variants.forEachIndexed { index, variant ->
            processStream(
                variant.url,
                variant.urlInManifest,
                MAIN_STREAM,
                index,
                updatedManifestBuilder
            )
        }

        hlsPlaylist.videos.forEachIndexed { index, rendition ->
            if (rendition.url == null || rendition.urlInManifest == null) return@forEachIndexed

            processStream(
                rendition.url,
                rendition.urlInManifest,
                MAIN_STREAM,
                index,
                updatedManifestBuilder
            )
        }

        hlsPlaylist.audios.forEachIndexed { index, rendition ->
            if (rendition.url == null || rendition.urlInManifest == null) return@forEachIndexed

            processStream(
                rendition.url,
                rendition.urlInManifest,
                SECONDARY_STREAM,
                index,
                updatedManifestBuilder
            )
        }

        hlsPlaylist.subtitles.forEach { rendition ->
            if (rendition.url != null && rendition.urlInManifest != null && rendition.url != rendition.urlInManifest) {
                replaceUrlInManifest(updatedManifestBuilder, rendition.urlInManifest, rendition.url)
            }
        }

        hlsPlaylist.closedCaptions.forEach { rendition ->
            if (rendition.url != null && rendition.urlInManifest != null && rendition.url != rendition.urlInManifest) {
                replaceUrlInManifest(updatedManifestBuilder, rendition.urlInManifest, rendition.url)
            }
        }

        return updatedManifestBuilder.toString()
    }

    private suspend fun parseMediaPlaylist(
        manifestUrl: String,
        mediaPlaylist: HlsMediaPlaylist,
        originalManifest: String
    ): String {
        val mediaType = streams[manifestUrl]?.type ?: MAIN_STREAM
        val isStreamLive = !mediaPlaylist.hasEndTag
        val newMediaSequence = mediaPlaylist.mediaSequence
        val updatedManifestBuilder = StringBuilder(originalManifest)

        val segmentsToRemove = removeObsoleteSegments(manifestUrl, newMediaSequence)
        val segmentsToAdd = mutableListOf<Segment>()

        val initialStartTime = getInitialStartTime(isStreamLive, mediaPlaylist)
        clearCurrentSegmentRuntimeIds(mediaType)


        var searchOffset = 0
        var lastProcessedInitSegment: InitializationSegment? = null

        mediaPlaylist.hlsSegments.forEachIndexed { index, segment ->
            if (segment.initializationSegment != null && segment.initializationSegment !== lastProcessedInitSegment) {
                val initSeg = segment.initializationSegment
                val encodedInitUrl = encodeUrlToBase64(initSeg.absoluteUrl)
                val newInitUrl = urlFactory.buildSegmentUrl(encodedInitUrl)

                searchOffset = replaceUrlInManifest(updatedManifestBuilder, initSeg.url, newInitUrl, searchOffset)
                lastProcessedInitSegment = initSeg
            }

            val segmentIndex = index + newMediaSequence
            addCurrentSegmentRuntimeId(mediaType, segment.runtimeUrl)

            val encodedSegmentUrl = segment.byteRange?.let {
                encodeUrlToBase64("${segment.absoluteUrl}|${it.start}-${it.end}")
            } ?: encodeUrlToBase64(segment.absoluteUrl)

            val newSegmentUrl = urlFactory.buildSegmentUrl(encodedSegmentUrl)
            searchOffset = replaceUrlInManifest(updatedManifestBuilder, segment.url, newSegmentUrl, searchOffset)

            val newSegment = addNewSegment(manifestUrl, segmentIndex, initialStartTime, segment)
            if (newSegment != null) segmentsToAdd.add(newSegment)
        }

        updateStreamData(manifestUrl, segmentsToAdd, segmentsToRemove, isStreamLive)

        if (!streams.containsKey(manifestUrl)) {
            streams[manifestUrl] = Stream(runtimeId = manifestUrl, type = MAIN_STREAM, index = 0)
        }

        logger.d { "Segments updated. Added: ${segmentsToAdd.size}, Removed: ${segmentsToRemove.size}" }

        return updatedManifestBuilder.toString()
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
            url = hlsSegment.absoluteUrl,
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
        index: Int,
        updatedManifestBuilder: StringBuilder
    ) {
        if (!streams.containsKey(absoluteStreamUrl)) {
            streams[absoluteStreamUrl] = Stream(runtimeId = absoluteStreamUrl, type = streamType, index = index)
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

    private fun removeObsoleteSegments(variantUrl: String, removeUntilId: Long): List<String> {
        val segmentsMap = streamSegments[variantUrl] ?: return emptyList()
        val obsoleteSegments = segmentsMap.filterKeys { it < removeUntilId }

        obsoleteSegments.forEach { (id, _) -> segmentsMap.remove(id) }

        return obsoleteSegments.values.map { it.runtimeId }
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
        clearCurrentSegmentRuntimeIds()
    }
}
