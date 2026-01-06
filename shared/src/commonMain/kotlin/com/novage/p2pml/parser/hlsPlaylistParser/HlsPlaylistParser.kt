package com.novage.p2pml.parser.hlsPlaylistParser

import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.MICROS_PER_SECOND
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.PLAYLIST_HEADER
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.REGEX_AUDIO
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.REGEX_BYTERANGE
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.REGEX_CLOSED_CAPTIONS
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.REGEX_GROUP_ID
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.REGEX_MEDIA_DURATION
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.REGEX_MEDIA_SEQUENCE
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.REGEX_NAME
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.REGEX_SUBTITLES
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.REGEX_TYPE
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.REGEX_URI
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.REGEX_VALUE
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.REGEX_VARIABLE_REFERENCE
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.REGEX_VIDEO
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_BYTERANGE
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_DEFINE
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_DISCONTINUITY
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_DISCONTINUITY_SEQUENCE
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_ENDLIST
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_INIT_SEGMENT
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_I_FRAME_STREAM_INF
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_KEY
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_MEDIA
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_MEDIA_DURATION
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_MEDIA_SEQUENCE
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_STREAM_INF
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_TARGET_DURATION
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TYPE_AUDIO
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TYPE_CLOSED_CAPTIONS
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TYPE_SUBTITLES
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TYPE_VIDEO
import com.novage.p2pml.utils.CoreLogger
import kotlin.math.roundToLong

private const val UTF8_BOM_BYTE_1 = 0xEF
private const val UTF8_BOM_BYTE_2 = 0xBB
private const val UTF8_BOM_BYTE_3 = 0xBF

internal class HlsPlaylistParser {
    private val logger = CoreLogger("HlsPlaylistParser")
    fun parse(playlistUri: String, playlistData: String): HlsPlaylist {
        val reader = Reader(playlistData)
        val extraLines = ArrayDeque<String>()

        try {
            if (!checkPlaylistHeader(reader)) {
                logger.e { "Playlist missing #EXTM3U header: $playlistUri" }
                error("Invalid playlist header")
            }

            var line = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()

                if (trimmed.isEmpty()) {
                    // Ignore empty lines.
                } else if (trimmed.startsWith(TAG_STREAM_INF)) {
                    extraLines.add(line)
                    return parseMultivariantPlaylist(LineIterator(extraLines, reader), playlistUri)
                } else if (
                    trimmed.startsWith(TAG_TARGET_DURATION) ||
                    trimmed.startsWith(TAG_MEDIA_SEQUENCE) ||
                    trimmed.startsWith(TAG_MEDIA_DURATION) ||
                    trimmed.startsWith(TAG_KEY) ||
                    trimmed.startsWith(TAG_BYTERANGE) ||
                    trimmed == TAG_DISCONTINUITY ||
                    trimmed == TAG_DISCONTINUITY_SEQUENCE ||
                    trimmed == TAG_ENDLIST
                ) {
                    extraLines.add(line)
                    return parseMediaPlaylist(LineIterator(extraLines, reader), playlistUri)
                } else {
                    extraLines.add(line)
                }
                line = reader.readLine()
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to parse playlist: ${e.message}" }
            throw e
        }

        logger.e { "Failed to parse playlist: No valid tags found." }
        error("Failed to parse playlist: No valid tags found")
    }

    private fun parseMediaPlaylist(iterator: LineIterator, playlistUri: String): HlsPlaylist {
        var mediaSequence = 0L
        var hasEndTag = false
        val hlsSegments = mutableListOf<HlsSegment>()
        val variableDefinitions = mutableMapOf<String, String>()
        var initializationSegment: InitializationSegment? = null

        var segmentDurationUs = 0L
        var segmentByteRangeOffset = 0L
        var segmentByteRangeLength = -1L

        while (iterator.hasNext()) {
            val line = iterator.next()

            if (line.isEmpty()) continue

            if (line.startsWith("#")) {
                when {
                    line.startsWith(TAG_MEDIA_SEQUENCE) -> {
                        mediaSequence = parseLongAttr(line, REGEX_MEDIA_SEQUENCE)
                    }
                    line.startsWith(TAG_ENDLIST) -> {
                        hasEndTag = true
                    }
                    line.startsWith(TAG_MEDIA_DURATION) -> {
                        segmentDurationUs = parseTimeSecondsToUs(line, REGEX_MEDIA_DURATION)
                    }
                    line.startsWith(TAG_BYTERANGE) -> {
                        val byteRange = parseStringAttr(line, REGEX_BYTERANGE, variableDefinitions)
                        val splitByteRange = splitString(byteRange, "@")

                        segmentByteRangeLength = splitByteRange[0].toLong()
                        if (splitByteRange.size > 1) {
                            segmentByteRangeOffset = splitByteRange[1].toLong()
                        }
                    }
                    line.startsWith(TAG_INIT_SEGMENT) -> {
                        val uri = parseStringAttr(line, REGEX_URI, variableDefinitions)

                        initializationSegment =
                            InitializationSegment(
                                url = uri,
                                absoluteUrl = resolve(playlistUri, uri)
                            )
                    }
                }
            } else {
                val segmentUri = replaceVariableReferences(line, variableDefinitions)
                val absoluteUri =
                    if (isAbsolute(segmentUri)) segmentUri else resolve(playlistUri, segmentUri)

                val hlsSegment =
                    HlsSegment(
                        url = segmentUri,
                        absoluteUrl = absoluteUri,
                        byteRangeOffset = segmentByteRangeOffset,
                        byteRangeLength = segmentByteRangeLength,
                        durationUs = segmentDurationUs,
                        initializationSegment = initializationSegment
                    )

                hlsSegments.add(hlsSegment)

                if (segmentByteRangeLength != -1L) segmentByteRangeOffset += segmentByteRangeLength

                segmentDurationUs = 0L
                segmentByteRangeLength = -1L
            }
        }

        return HlsMediaPlaylist(
            baseUri = playlistUri,
            mediaSequence = mediaSequence,
            hasEndTag = hasEndTag,
            hlsSegments = hlsSegments
        )
    }

    private fun parseMultivariantPlaylist(iterator: LineIterator, baseUri: String): HlsPlaylist {
        val variableDefinitions = mutableMapOf<String, String>()
        val variants = mutableListOf<Variant>()
        val videos = mutableListOf<Rendition>()
        val audios = mutableListOf<Rendition>()
        val subtitles = mutableListOf<Rendition>()
        val closedCaptions = mutableListOf<Rendition>()
        val mediaTags = mutableListOf<String>()

        while (iterator.hasNext()) {
            val line = iterator.next()

            val isIFrameOnlyVariant = line.startsWith(TAG_I_FRAME_STREAM_INF)

            when {
                line.startsWith(TAG_DEFINE) -> {
                    val key = parseStringAttr(line, REGEX_NAME, variableDefinitions)
                    val value = parseStringAttr(line, REGEX_VALUE, variableDefinitions)

                    variableDefinitions[key] = value
                }
                line.startsWith(TAG_MEDIA) -> {
                    mediaTags.add(line)
                }
                line.startsWith(TAG_STREAM_INF) || isIFrameOnlyVariant -> {
                    val videoGroupId =
                        parseOptionalStringAttr(line, REGEX_VIDEO, variableDefinitions)
                    val audioGroupId =
                        parseOptionalStringAttr(line, REGEX_AUDIO, variableDefinitions)
                    val subtitleGroupId =
                        parseOptionalStringAttr(line, REGEX_SUBTITLES, variableDefinitions)
                    val captionGroupId =
                        parseOptionalStringAttr(line, REGEX_CLOSED_CAPTIONS, variableDefinitions)

                    var variantUrlInManifest: String
                    val variantUri =
                        if (isIFrameOnlyVariant) {
                            variantUrlInManifest =
                                parseStringAttr(line, REGEX_URI, variableDefinitions)
                            resolve(baseUri, variantUrlInManifest)
                        } else {
                            if (!iterator.hasNext()) throw Exception("Unexpected end of variant")

                            val nextLine = iterator.next()
                            val replaced = replaceVariableReferences(nextLine, variableDefinitions)
                            variantUrlInManifest = replaced

                            resolve(baseUri, replaced)
                        }

                    variants.add(
                        Variant(
                            url = variantUri,
                            urlInManifest = variantUrlInManifest,
                            videoGroupId = videoGroupId,
                            audioGroupId = audioGroupId,
                            subtitleGroupId = subtitleGroupId,
                            captionGroupId = captionGroupId
                        )
                    )
                }
            }
        }

        for (mediaLine in mediaTags) {
            val groupId = parseStringAttr(mediaLine, REGEX_GROUP_ID, variableDefinitions)
            val name = parseStringAttr(mediaLine, REGEX_NAME, variableDefinitions)
            val type = parseStringAttr(mediaLine, REGEX_TYPE, variableDefinitions)
            val referenceUri = parseOptionalStringAttr(mediaLine, REGEX_URI, variableDefinitions)
            val resolvedUri = referenceUri?.let { resolve(baseUri, it) }

            when (type) {
                TYPE_VIDEO ->
                    videos.add(
                        Rendition(
                            url = resolvedUri,
                            urlInManifest = referenceUri,
                            groupId = groupId,
                            name = name
                        )
                    )
                TYPE_AUDIO ->
                    audios.add(
                        Rendition(
                            url = resolvedUri,
                            urlInManifest = referenceUri,
                            groupId = groupId,
                            name = name
                        )
                    )
                TYPE_SUBTITLES ->
                    subtitles.add(
                        Rendition(
                            url = resolvedUri,
                            urlInManifest = referenceUri,
                            groupId = groupId,
                            name = name
                        )
                    )
                TYPE_CLOSED_CAPTIONS ->
                    closedCaptions.add(
                        Rendition(
                            url = resolvedUri,
                            urlInManifest = referenceUri,
                            groupId = groupId,
                            name = name
                        )
                    )
            }
        }

        return HlsMultivariantPlaylist(
            baseUri = baseUri,
            variants = variants,
            videos = videos,
            audios = audios,
            subtitles = subtitles,
            closedCaptions = closedCaptions
        )
    }

    private fun parseTimeSecondsToUs(line: String, regex: Regex): Long {
        val timeValueSeconds = parseStringAttr(line, regex, emptyMap())

        return (timeValueSeconds.toDouble() * MICROS_PER_SECOND).roundToLong()
    }

    private fun checkPlaylistHeader(reader: Reader): Boolean {
        var last = reader.read()
        if (last == UTF8_BOM_BYTE_1) {
            if (reader.read() != UTF8_BOM_BYTE_2 || reader.read() != UTF8_BOM_BYTE_3) return false

            last = reader.read()
        }

        last = skipIgnorableWhitespace(reader, skipLinebreaks = true, last)
        for (c in PLAYLIST_HEADER) {
            if (last != c.code) return false

            last = reader.read()
        }

        last = skipIgnorableWhitespace(reader, skipLinebreaks = false, last)

        return isLinebreak(last)
    }

    private fun replaceVariableReferences(string: String, variableDefinitions: Map<String, String>): String =
        REGEX_VARIABLE_REFERENCE.replace(string) { matchResult ->
            val groupName = matchResult.groupValues[1]
            variableDefinitions[groupName]?.let { Regex.escapeReplacement(it) } ?: ""
        }

    private fun parseStringAttr(line: String, regex: Regex, variableDefinitions: Map<String, String>): String =
        parseOptionalStringAttr(line, regex, null, variableDefinitions)
            ?: throw Exception("Missing required attribute")

    private fun parseOptionalStringAttr(line: String, regex: Regex, variableDefinitions: Map<String, String>): String? =
        parseOptionalStringAttr(line, regex, null, variableDefinitions)

    private fun parseOptionalStringAttr(
        line: String,
        regex: Regex,
        defaultValue: String?,
        variableDefinitions: Map<String, String>
    ): String? {
        val value = regex.find(line)?.groups?.get(1)?.value ?: defaultValue
        return if (variableDefinitions.isEmpty() || value == null) {
            value
        } else {
            replaceVariableReferences(value, variableDefinitions)
        }
    }

    private fun parseLongAttr(line: String, regex: Regex): Long = parseStringAttr(line, regex, emptyMap()).toLong()
}
