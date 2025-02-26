package com.novage.p2pml.parser

import com.novage.p2pml.RuntimeSegment
import com.novage.p2pml.Stream
import com.novage.p2pml.UpdateStreamParams
import com.novage.p2pml.parser.hlsPlaylistParser.HlsMediaPlaylist
import com.novage.p2pml.parser.hlsPlaylistParser.HlsMultivariantPlaylist
import com.novage.p2pml.parser.hlsPlaylistParser.HlsPlaylistParser
import com.novage.p2pml.parser.hlsPlaylistParser.InitializationSegment
import com.novage.p2pml.parser.hlsPlaylistParser.Segment
import com.novage.p2pml.providers.PlaybackProvider
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val MAIN_STREAM = "main"
internal const val SECONDARY_STREAM = "secondary"

internal class HlsManifestParser(private val playbackProvider: PlaybackProvider) {
    private val parser = HlsPlaylistParser()
    private val mutex = Mutex()

    private var currentMasterManifestUrl: String? = null

    private val streams = mutableListOf<Stream>()
    private val streamSegments = mutableMapOf<String, MutableMap<Long, RuntimeSegment>>()
    private val updateStreamParams = mutableMapOf<String, UpdateStreamParams>()

    private val currentVideoSegmentRuntimeIds = mutableSetOf<String>()
    private val currentAudioSegmentRuntimeIds = mutableSetOf<String>()

    suspend fun getModifiedManifest(originalManifest: String, manifestUrl: String): String =
        mutex.withLock { parseHlsManifest(manifestUrl, originalManifest) }

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

    suspend fun isCurrentSegment(segmentUrl: String): Boolean =
        mutex.withLock {
            currentVideoSegmentRuntimeIds.contains(segmentUrl) ||
                currentAudioSegmentRuntimeIds.contains(segmentUrl)
        }

    suspend fun doesManifestExist(manifestUrl: String): Boolean =
        mutex.withLock {
            currentMasterManifestUrl == manifestUrl || streams.any { it.runtimeId == manifestUrl }
        }

    private suspend fun parseHlsManifest(manifestUrl: String, manifest: String): String {
        return when (val hlsPlaylist = parser.parse(manifestUrl, manifest)) {
            is HlsMediaPlaylist -> parseMediaPlaylist(manifestUrl, hlsPlaylist, manifest)
            is HlsMultivariantPlaylist ->
                parseMultivariantPlaylist(manifestUrl, hlsPlaylist, manifest)
            else -> throw IllegalStateException("Unsupported playlist type")
        }
    }

    private fun parseMultivariantPlaylist(
        manifestUrl: String,
        hlsPlaylist: HlsMultivariantPlaylist,
        originalManifest: String,
    ): String {
        val updatedManifestBuilder = StringBuilder(originalManifest)

        currentMasterManifestUrl = manifestUrl

        hlsPlaylist.variants.forEachIndexed { index, variant ->
            processStream(
                variant.url,
                variant.urlInManifest,
                MAIN_STREAM,
                index,
                updatedManifestBuilder,
            )
        }

        hlsPlaylist.audios.forEachIndexed { index, rendition ->
            if (rendition.url == null || rendition.urlInManifest == null) return@forEachIndexed

            processStream(
                rendition.url,
                rendition.urlInManifest,
                SECONDARY_STREAM,
                index,
                updatedManifestBuilder,
            )
        }

        hlsPlaylist.subtitles.forEach { rendition ->
            if (rendition.url == null || rendition.urlInManifest == null) return@forEach
            if (rendition.url == rendition.urlInManifest) return@forEach

            replaceUrlInManifest(updatedManifestBuilder, rendition.urlInManifest, rendition.url)
        }

        hlsPlaylist.closedCaptions.forEach { rendition ->
            if (rendition.url == null || rendition.urlInManifest == null) return@forEach
            if (rendition.url == rendition.urlInManifest) return@forEach

            replaceUrlInManifest(updatedManifestBuilder, rendition.urlInManifest, rendition.url)
        }

        return updatedManifestBuilder.toString()
    }

    private fun removeObsoleteSegments(variantUrl: String, removeUntilId: Long): List<String> {
        val segmentsMap = streamSegments[variantUrl] ?: return emptyList()
        val obsoleteSegments = segmentsMap.filterKeys { it < removeUntilId }

        obsoleteSegments.forEach { (id, _) -> segmentsMap.remove(id) }

        return obsoleteSegments.values.map { it.runtimeId }
    }

    private suspend fun getInitialStartTime(
        isLive: Boolean,
        mediaPlaylist: HlsMediaPlaylist,
    ): Double =
        if (isLive) {
            playbackProvider.getAbsolutePlaybackPosition(mediaPlaylist)
        } else {
            0.0
        }

    private suspend fun parseMediaPlaylist(
        manifestUrl: String,
        mediaPlaylist: HlsMediaPlaylist,
        originalManifest: String,
    ): String {

        // If there is no master manifest, the stream type is MAIN_STREAM
        val mediaType = streams.find { it.runtimeId == manifestUrl }?.type ?: MAIN_STREAM

        val isStreamLive = !mediaPlaylist.hasEndTag
        val newMediaSequence = mediaPlaylist.mediaSequence
        val updatedManifestBuilder = StringBuilder(originalManifest)

        val segmentsToRemove = removeObsoleteSegments(manifestUrl, newMediaSequence)
        val segmentsToAdd = mutableListOf<RuntimeSegment>()
        val initializationSegments = mutableSetOf<InitializationSegment>()

        val initialStartTime = getInitialStartTime(isStreamLive, mediaPlaylist)

        clearCurrentSegmentRuntimeIds(mediaType)
        mediaPlaylist.segments.forEachIndexed { index, segment ->
            if (segment.initializationSegment != null)
                initializationSegments.add(segment.initializationSegment)

            val segmentIndex = index + newMediaSequence

            addCurrentSegmentRuntimeId(mediaType, segment.runtimeUrl)
            processSegment(segment, updatedManifestBuilder)

            val newSegment = addNewSegment(manifestUrl, segmentIndex, initialStartTime, segment)

            if (newSegment != null) segmentsToAdd.add(newSegment)
        }

        initializationSegments.forEach { segment ->
            if (segment.url == segment.absoluteUrl) return@forEach

            replaceUrlInManifest(updatedManifestBuilder, segment.url, segment.absoluteUrl)
        }

        updateStreamData(manifestUrl, segmentsToAdd, segmentsToRemove, isStreamLive)

        val stream = findStreamByRuntimeId(manifestUrl)
        // This should be fired if there is no master manifest
        if (stream == null) {
            streams.add(Stream(runtimeId = manifestUrl, type = MAIN_STREAM, index = 0))
        }

        return updatedManifestBuilder.toString()
    }

    private fun findStreamByRuntimeId(runtimeId: String): Stream? =
        streams.find { it.runtimeId == runtimeId }

    private fun updateStreamData(
        variantUrl: String,
        newSegments: List<RuntimeSegment>,
        segmentsToRemove: List<String>,
        isLive: Boolean,
    ) {
        val updateStream =
            UpdateStreamParams(
                streamRuntimeId = variantUrl,
                addSegments = newSegments,
                removeSegmentsIds = segmentsToRemove,
                isLive = isLive,
            )

        updateStreamParams[variantUrl] = updateStream
    }

    private fun addNewSegment(
        variantUrl: String,
        segmentId: Long,
        initialStartTime: Double,
        segment: Segment,
    ): RuntimeSegment? {
        val segmentsMap = streamSegments.getOrPut(variantUrl) { mutableMapOf() }
        if (segmentsMap.contains(segmentId)) return null

        val prevSegment = segmentsMap[segmentId - 1]

        val segmentDurationInSeconds = segment.durationUs / 1_000_000.0
        val startTime = prevSegment?.endTime ?: initialStartTime
        val endTime = startTime + segmentDurationInSeconds

        val newSegment =
            RuntimeSegment(
                runtimeId = segment.runtimeUrl,
                externalId = segmentId,
                url = segment.absoluteUrl,
                byteRange = segment.byteRange,
                startTime = startTime,
                endTime = endTime,
            )

        segmentsMap[segmentId] = newSegment

        return newSegment
    }

    private fun processSegment(segment: Segment, manifestBuilder: StringBuilder) {
        val byteRange = segment.byteRange
        val absoluteSegmentUrl = segment.absoluteUrl
        val segmentUrlInManifest =
            if (segment.url == absoluteSegmentUrl) absoluteSegmentUrl else segment.url

        val encodedAbsoluteSegmentUrl =
            if (byteRange != null) {
                encodeUrlToBase64("${absoluteSegmentUrl}|${byteRange.start}-${byteRange.end}")
            } else {
                encodeUrlToBase64(absoluteSegmentUrl)
            }

        // TODO: Move port to configuration
        val newSegmentUrl = getLocalhostUrl(8080, "segment/$encodedAbsoluteSegmentUrl")

        val startIndex =
            manifestBuilder.indexOf(segmentUrlInManifest).takeIf { it != -1 }
                ?: throw IllegalStateException("URL not found in manifest: $segment.url")
        val endIndex = startIndex + segmentUrlInManifest.length

        // for some reason, replaceRange doesn't work in iOS
        // manifestBuilder.replaceRange(startIndex, endIndex, newSegmentUrl)
        manifestBuilder.deleteRange(startIndex, endIndex)
        manifestBuilder.insert(startIndex, newSegmentUrl)
    }

    private fun processStream(
        absoluteStreamUrl: String,
        streamUrlInManifest: String,
        streamType: String,
        index: Int,
        updatedManifestBuilder: StringBuilder,
    ) {
        streams.add(Stream(runtimeId = absoluteStreamUrl, type = streamType, index = index))

        val encodedUrl = absoluteStreamUrl.encodeURLParameter()
        val newUrl = getLocalhostUrl(8080, "manifest/${encodedUrl}")

        replaceUrlInManifest(updatedManifestBuilder, streamUrlInManifest, newUrl)
    }

    private fun replaceUrlInManifest(
        updatedManifestBuilder: StringBuilder,
        streamUrlInManifest: String,
        newUrl: String,
    ) {
        val startIndex =
            updatedManifestBuilder.indexOf(streamUrlInManifest).takeIf { it != -1 }
                ?: throw IllegalStateException("URL not found in manifest: $streamUrlInManifest")

        val endIndex = startIndex + streamUrlInManifest.length

        // for some reason, replaceRange doesn't work in iOS
        // updatedManifestBuilder.replaceRange(startIndex, endIndex, newUrl)
        updatedManifestBuilder.deleteRange(startIndex, endIndex)
        updatedManifestBuilder.insert(startIndex, newUrl)
    }

    suspend fun reset() {
        mutex.withLock {
            streams.clear()
            streamSegments.clear()
            updateStreamParams.clear()
            clearCurrentSegmentRuntimeIds()
        }
    }
}
