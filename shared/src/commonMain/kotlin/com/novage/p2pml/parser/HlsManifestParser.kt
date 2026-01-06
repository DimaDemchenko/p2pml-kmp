package com.novage.p2pml.parser

import com.novage.p2pml.domain.models.Segment
import com.novage.p2pml.parser.hlsPlaylistParser.*
import com.novage.p2pml.domain.interfaces.PlaybackProvider
import com.novage.p2pml.domain.models.PlaylistSnapshot
import com.novage.p2pml.parser.encoding.encodeUrlToBase64
import com.novage.p2pml.server.config.LocalUrlFactory
import com.novage.p2pml.utils.CoreLogger
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

internal const val MAIN_STREAM = "main"
internal const val SECONDARY_STREAM = "secondary"

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

    suspend fun getModifiedManifest(originalManifest: String, manifestUrl: String): String =
        mutex.withLock {
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

    suspend fun isCurrentSegment(segmentUrl: String): Boolean =
        mutex.withLock {
            currentVideoSegmentRuntimeIds.contains(segmentUrl) ||
                    currentAudioSegmentRuntimeIds.contains(segmentUrl)
        }

    suspend fun isManifestTracked(manifestUrl: String): Boolean =
        mutex.withLock {
            currentMasterManifestUrl == manifestUrl || streams.containsKey(manifestUrl)
        }

    private suspend fun parseHlsManifest(manifestUrl: String, manifest: String): String {
        return when (val hlsPlaylist = parser.parse(manifestUrl, manifest)) {
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
                throw IllegalStateException("Unsupported playlist type")
            }
        }
    }

    private fun parseMultivariantPlaylist(
        manifestUrl: String,
        hlsPlaylist: HlsMultivariantPlaylist,
        originalManifest: String,
    ): String {
        val updatedManifestBuilder = StringBuilder(originalManifest)

        currentMasterManifestUrl = manifestUrl

        logger.i { "Processing Master Playlist. Variants: ${hlsPlaylist.variants.size}, Audio: ${hlsPlaylist.audios.size}" }

        hlsPlaylist.variants.forEachIndexed { index, variant ->
            processStream(
                variant.url,
                variant.urlInManifest,
                MAIN_STREAM,
                index,
                updatedManifestBuilder,
            )
        }

        hlsPlaylist.videos.forEachIndexed { index, rendition ->
            if (rendition.url == null || rendition.urlInManifest == null) return@forEachIndexed

            processStream(
                rendition.url,
                rendition.urlInManifest,
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

    private fun removeObsoleteSegments(variantUrl: String, removeUntilId: Long): List<String> {
        val segmentsMap = streamSegments[variantUrl] ?: return emptyList()
        val obsoleteSegments = segmentsMap.filterKeys { it < removeUntilId }

        obsoleteSegments.forEach { (id, _) -> segmentsMap.remove(id) }

        return obsoleteSegments.values.map { it.runtimeId }
    }

    private suspend fun getInitialStartTime(
        isLive: Boolean,
        mediaPlaylist: HlsMediaPlaylist,
    ): Double {
        if (isLive) {
            val snapshot = PlaylistSnapshot(
                mediaSequence = mediaPlaylist.mediaSequence,
                hasEndTag = mediaPlaylist.hasEndTag,
                segmentDurations = mediaPlaylist.hlsSegments.map {
                    it.durationUs / 1_000_000.0
                }
            )
            return playbackProvider.getAbsolutePlaybackPosition(snapshot)
        } else {
            return 0.0
        }
    }

    private suspend fun parseMediaPlaylist(
        manifestUrl: String,
        mediaPlaylist: HlsMediaPlaylist,
        originalManifest: String,
    ): String {
        val mediaType = streams[manifestUrl]?.type ?: MAIN_STREAM

        val isStreamLive = !mediaPlaylist.hasEndTag
        val newMediaSequence = mediaPlaylist.mediaSequence
        val updatedManifestBuilder = StringBuilder(originalManifest)

        val segmentsToRemove = removeObsoleteSegments(manifestUrl, newMediaSequence)
        val segmentsToAdd = mutableListOf<Segment>()
        val initializationSegments = mutableSetOf<InitializationSegment>()

        val initialStartTime = getInitialStartTime(isStreamLive, mediaPlaylist)

        clearCurrentSegmentRuntimeIds(mediaType)

        mediaPlaylist.hlsSegments.forEachIndexed { index, segment ->
            if (segment.initializationSegment != null)
                initializationSegments.add(segment.initializationSegment)

            val segmentIndex = index + newMediaSequence

            addCurrentSegmentRuntimeId(mediaType, segment.runtimeUrl)
            processSegment(segment, updatedManifestBuilder)

            val newSegment = addNewSegment(manifestUrl, segmentIndex, initialStartTime, segment)

            if (newSegment != null) segmentsToAdd.add(newSegment)
        }

        initializationSegments.forEach { segment ->
            if (segment.url != segment.absoluteUrl) {
                replaceUrlInManifest(updatedManifestBuilder, segment.url, segment.absoluteUrl)
            }
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
        isLive: Boolean,
    ) {
        val updateStream = UpdateStreamParams(
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
        hlsSegment: HlsSegment,
    ): Segment? {
        val segmentsMap = streamSegments.getOrPut(variantUrl) { mutableMapOf() }
        if (segmentsMap.contains(segmentId)) return null

        val prevSegment = segmentsMap[segmentId - 1]
        val segmentDurationInSeconds = hlsSegment.durationUs / 1_000_000.0

        val startTime = prevSegment?.endTime ?: initialStartTime
        val endTime = startTime + segmentDurationInSeconds

        val newSegment = Segment(
            runtimeId = hlsSegment.runtimeUrl,
            externalId = segmentId,
            url = hlsSegment.absoluteUrl,
            byteRange = hlsSegment.byteRange,
            startTime = startTime,
            endTime = endTime,
        )

        segmentsMap[segmentId] = newSegment
        return newSegment
    }

    private fun processSegment(hlsSegment: HlsSegment, manifestBuilder: StringBuilder) {
        val byteRange = hlsSegment.byteRange
        val absoluteSegmentUrl = hlsSegment.absoluteUrl
        val segmentUrlInManifest = hlsSegment.url

        val encodedAbsoluteSegmentUrl = if (byteRange != null) {
            encodeUrlToBase64("${absoluteSegmentUrl}|${byteRange.start}-${byteRange.end}")
        } else {
            encodeUrlToBase64(absoluteSegmentUrl)
        }

        val newSegmentUrl = urlFactory.buildSegmentUrl(encodedAbsoluteSegmentUrl)
        replaceUrlInManifest(manifestBuilder, segmentUrlInManifest, newSegmentUrl)
    }

    private fun processStream(
        absoluteStreamUrl: String,
        streamUrlInManifest: String,
        streamType: String,
        index: Int,
        updatedManifestBuilder: StringBuilder,
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
    ) {
        val startIndex = updatedManifestBuilder.indexOf(oldUrl).takeIf { it != -1 }

        if (startIndex == null) {
            logger.w { "Failed to rewrite URL. Original string not found in manifest: $oldUrl" }
            return
        }

        val endIndex = startIndex + oldUrl.length
        updatedManifestBuilder.deleteRange(startIndex, endIndex)
        updatedManifestBuilder.insert(startIndex, newUrl)
    }

    suspend fun getUpdateStreamParamsJson(variantUrl: String): String? = mutex.withLock {
        val params = updateStreamParams[variantUrl] ?: return null
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
