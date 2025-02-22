package com.novage.p2pml.parser.hlsPlaylistParser

import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.BOOLEAN_TRUE
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
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_INDEPENDENT_SEGMENTS
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_INIT_SEGMENT
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_I_FRAME_STREAM_INF
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_KEY
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_MEDIA
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_MEDIA_DURATION
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_MEDIA_SEQUENCE
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_PREFIX
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_STREAM_INF
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TAG_TARGET_DURATION
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TYPE_AUDIO
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TYPE_CLOSED_CAPTIONS
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TYPE_SUBTITLES
import com.novage.p2pml.parser.hlsPlaylistParser.HlsConstants.TYPE_VIDEO

class HlsPlaylistParser {
    fun parse(playlistUri: String, playlistData: String): HlsPlaylist {
        val reader = Reader(playlistData)
        val extraLines = ArrayDeque<String>()

        try {
            if (!checkPlaylistHeader(reader)) throw Exception("Invalid playlist header")

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
            println("Error: $e")
            // Ignore parsing errors.
        }
        throw Exception("Failed to parse playlist")
    }

    private fun parseMediaPlaylist(iterator: LineIterator, playlistUri: String): HlsPlaylist {
        var mediaSequence = 0L
        var hasEndTag = false
        var initializationSegment: Segment? = null
        val segments = mutableListOf<Segment>()
        val variableDefinitions = mutableMapOf<String, String>()

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
                        if (splitByteRange.size > 1)
                            segmentByteRangeOffset = splitByteRange[1].toLong()
                    }
                    line.startsWith(TAG_INIT_SEGMENT) -> {
                        val uri = parseStringAttr(line, REGEX_URI, variableDefinitions)
                        val byteRange =
                            parseOptionalStringAttr(line, REGEX_BYTERANGE, variableDefinitions)

                        if (byteRange != null) {
                            val splitByteRange = splitString(byteRange, "@")

                            segmentByteRangeLength = splitByteRange[0].toLong()
                            if (splitByteRange.size > 1)
                                segmentByteRangeOffset = splitByteRange[1].toLong()
                        }

                        if (segmentByteRangeLength == -1L) segmentByteRangeLength = 0

                        initializationSegment =
                            Segment(
                                url = uri,
                                byteRangeOffset = segmentByteRangeOffset,
                                byteRangeLength = segmentByteRangeLength,
                                durationUs = segmentDurationUs,
                                initializationSegment = null,
                            )
                    }
                }
            } else {
                val segmentUri = replaceVariableReferences(line, variableDefinitions)
                val resolvedUri = resolve(playlistUri, segmentUri)

                val segment =
                    Segment(
                        url = resolvedUri,
                        initializationSegment = initializationSegment,
                        byteRangeOffset = segmentByteRangeOffset,
                        byteRangeLength = segmentByteRangeLength,
                        durationUs = segmentDurationUs,
                    )

                segments.add(segment)

                if (segmentByteRangeLength != -1L) segmentByteRangeOffset += segmentByteRangeLength

                segmentDurationUs = 0L
                segmentByteRangeLength = -1L
            }
        }

        return HlsMediaPlaylist(
            baseUri = playlistUri,
            initializationSegment = initializationSegment,
            mediaSequence = mediaSequence,
            hasEndTag = hasEndTag,
            segments = segments,
        )
    }

    private fun parseMultivariantPlaylist(iterator: LineIterator, baseUri: String): HlsPlaylist {
        val variableDefinitions = mutableMapOf<String, String>()
        val variants = mutableListOf<Variant>()
        val videos = mutableListOf<Rendition>()
        val audios = mutableListOf<Rendition>()
        val subtitles = mutableListOf<Rendition>()
        val closedCaptions = mutableListOf<Rendition>()
        val tags = mutableListOf<String>()
        val mediaTags = mutableListOf<String>()
        var hasIndependentSegmentsTag = false

        while (iterator.hasNext()) {
            val line = iterator.next()

            if (line.startsWith(TAG_PREFIX)) {
                // We expose all tags through the playlist.
                tags.add(line)
            }

            val isIFrameOnlyVariant = line.startsWith(TAG_I_FRAME_STREAM_INF)

            when {
                line.startsWith(TAG_DEFINE) -> {
                    val key = parseStringAttr(line, REGEX_NAME, variableDefinitions)
                    val value = parseStringAttr(line, REGEX_VALUE, variableDefinitions)

                    variableDefinitions[key] = value
                }
                line == TAG_INDEPENDENT_SEGMENTS -> {
                    hasIndependentSegmentsTag = true
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

                    val variantUri =
                        if (isIFrameOnlyVariant) {
                            resolve(baseUri, parseStringAttr(line, REGEX_URI, variableDefinitions))
                        } else {
                            if (!iterator.hasNext()) throw Exception("Unexpected end of variant")

                            val nextLine = iterator.next()
                            val replaced = replaceVariableReferences(nextLine, variableDefinitions)

                            resolve(baseUri, replaced)
                        }

                    // val relativeUrl = getRelativePath(baseUri, variantUri)

                    variants.add(
                        Variant(
                            url = variantUri,
                            videoGroupId = videoGroupId,
                            audioGroupId = audioGroupId,
                            subtitleGroupId = subtitleGroupId,
                            captionGroupId = captionGroupId,
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
                    videos.add(Rendition(url = resolvedUri, groupId = groupId, name = name))
                TYPE_AUDIO ->
                    audios.add(Rendition(url = resolvedUri, groupId = groupId, name = name))
                TYPE_SUBTITLES ->
                    subtitles.add(Rendition(url = resolvedUri, groupId = groupId, name = name))
                TYPE_CLOSED_CAPTIONS ->
                    closedCaptions.add(Rendition(url = resolvedUri, groupId = groupId, name = name))
            }
        }

        return HlsMultivariantPlaylist(
            baseUri = baseUri,
            tags = tags,
            variants = variants,
            videos = videos,
            audios = audios,
            subtitles = subtitles,
            closedCaptions = closedCaptions,
            hasIndependentSegments = hasIndependentSegmentsTag,
            variableDefinitions = variableDefinitions,
        )
    }

    private fun parseTimeSecondsToUs(line: String, regex: Regex): Long {
        val timeValueSeconds = parseStringAttr(line, regex, emptyMap())
        return (timeValueSeconds.toDouble() * MICROS_PER_SECOND).toLong()
    }

    private fun checkPlaylistHeader(reader: Reader): Boolean {
        var last = reader.read()
        if (last == 0xEF) {
            if (reader.read() != 0xBB || reader.read() != 0xBF) return false

            // BOM detected and discarded.
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

    private fun replaceVariableReferences(
        string: String,
        variableDefinitions: Map<String, String>,
    ): String {
        return REGEX_VARIABLE_REFERENCE.replace(string) { matchResult ->
            val groupName = matchResult.groupValues[1]
            variableDefinitions[groupName]?.let { Regex.escapeReplacement(it) } ?: ""
        }
    }

    private fun parseStringAttr(
        line: String,
        regex: Regex,
        variableDefinitions: Map<String, String>,
    ): String {
        return parseOptionalStringAttr(line, regex, null, variableDefinitions)
            ?: throw Exception("Missing required attribute")
    }

    private fun parseOptionalStringAttr(
        line: String,
        regex: Regex,
        variableDefinitions: Map<String, String>,
    ): String? {
        return parseOptionalStringAttr(line, regex, null, variableDefinitions)
    }

    private fun parseOptionalStringAttr(
        line: String,
        regex: Regex,
        defaultValue: String?,
        variableDefinitions: Map<String, String>,
    ): String? {
        val value = regex.find(line)?.groups?.get(1)?.value ?: defaultValue
        return if (variableDefinitions.isEmpty() || value == null) {
            value
        } else {
            replaceVariableReferences(value, variableDefinitions)
        }
    }

    private fun parseOptionalBooleanAttribute(
        line: String,
        regex: Regex,
        defaultValue: Boolean,
    ): Boolean {
        val match = regex.find(line)
        // If a match is found, compare the first capture group to BOOLEAN_TRUE; otherwise, return
        // the default.
        return if (match != null) {
            match.groups[1]?.value == BOOLEAN_TRUE
        } else {
            defaultValue
        }
    }

    private fun parseDoubleAttr(line: String, regex: Regex): Double {
        return parseStringAttr(line, regex, emptyMap()).toDouble()
    }

    private fun parseOptionalDoubleAttr(line: String, regex: Regex, defaultValue: Double): Double {
        val match = regex.find(line)
        return match?.groups?.get(1)?.value?.toDouble() ?: defaultValue
    }

    private fun parseIntAttr(line: String, regex: Regex): Int {
        return parseStringAttr(line, regex, emptyMap()).toInt()
    }

    private fun parseLongAttr(line: String, regex: Regex): Long {
        return parseStringAttr(line, regex, emptyMap()).toLong()
    }
}
