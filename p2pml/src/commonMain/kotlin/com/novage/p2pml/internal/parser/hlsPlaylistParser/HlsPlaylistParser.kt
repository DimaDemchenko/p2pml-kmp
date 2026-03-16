package com.novage.p2pml.internal.parser.hlsPlaylistParser

import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.MICROS_PER_SECOND
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.PLAYLIST_HEADER
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_AUDIO
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_BYTERANGE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_CLOSED_CAPTIONS
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_GROUP_ID
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_MEDIA_DURATION
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_MEDIA_SEQUENCE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_NAME
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_SUBTITLES
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_TYPE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_URI
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_VALUE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_VARIABLE_REFERENCE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_VIDEO
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_BYTERANGE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_DEFINE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_DISCONTINUITY
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_DISCONTINUITY_SEQUENCE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_ENDLIST
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_INIT_SEGMENT
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_I_FRAME_STREAM_INF
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_KEY
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_MEDIA
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_MEDIA_DURATION
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_MEDIA_SEQUENCE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_PART
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_PRELOAD_HINT
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_RENDITION_REPORT
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_SESSION_KEY
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_STREAM_INF
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_TARGET_DURATION
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TYPE_AUDIO
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TYPE_CLOSED_CAPTIONS
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TYPE_SUBTITLES
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TYPE_VIDEO
import com.novage.p2pml.internal.utils.CoreLogger
import kotlin.math.roundToLong

private const val UTF8_BOM_BYTE_1 = 0xEF
private const val UTF8_BOM_BYTE_2 = 0xBB
private const val UTF8_BOM_BYTE_3 = 0xBF

internal class HlsPlaylistParser {
    private val logger = CoreLogger("HlsPlaylistParser")

    private data class SegmentState(
        var mediaSequence: Long = 0L,
        var hasEndTag: Boolean = false,
        var durationUs: Long = 0L,
        var offset: Long = 0L,
        var length: Long = -1L,
        var initSegment: InitializationSegment? = null,
        var encryptionKey: ParsedUrl? = null
    )

    fun parse(playlistUri: String, playlistData: String): HlsPlaylist {
        val reader = Reader(playlistData)
        val extraLines = ArrayDeque<String>()

        require(checkPlaylistHeader(reader)) {
            logger.e { "Playlist missing #EXTM3U header: $playlistUri" }
            "Invalid playlist header"
        }

        var line = reader.readLine()
        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                extraLines.add(line)
                when {
                    trimmed.startsWith(TAG_STREAM_INF) ->
                        return parseMultivariantPlaylist(LineIterator(extraLines, reader), playlistUri)

                    isMediaPlaylistTag(trimmed) ->
                        return parseMediaPlaylist(LineIterator(extraLines, reader), playlistUri)
                }
            }
            line = reader.readLine()
        }

        error("Failed to parse playlist: No valid tags found")
    }

    private fun isMediaPlaylistTag(trimmed: String): Boolean = trimmed.startsWith(TAG_TARGET_DURATION) ||
        trimmed.startsWith(TAG_MEDIA_SEQUENCE) ||
        trimmed.startsWith(TAG_MEDIA_DURATION) ||
        trimmed.startsWith(TAG_KEY) ||
        trimmed.startsWith(TAG_BYTERANGE) ||
        trimmed == TAG_DISCONTINUITY ||
        trimmed == TAG_DISCONTINUITY_SEQUENCE ||
        trimmed == TAG_ENDLIST

    private fun parseMediaPlaylist(iterator: LineIterator, playlistUri: String): HlsPlaylist {
        val state = SegmentState()
        val segments = mutableListOf<HlsSegment>()
        val parts = mutableListOf<ParsedUrl>()
        val preloadHints = mutableListOf<ParsedUrl>()
        val renditionReports = mutableListOf<ParsedUrl>()
        val vars = mutableMapOf<String, String>()

        while (iterator.hasNext()) {
            val line = iterator.next().trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#")) {
                processMediaTag(line, state, vars, playlistUri, parts, preloadHints, renditionReports)
            } else {
                segments.add(createSegment(line, playlistUri, vars, state))
                if (state.length != -1L) state.offset += state.length
                state.durationUs = 0L
                state.length = -1L
            }
        }

        return HlsMediaPlaylist(
            playlistUri,
            state.mediaSequence,
            state.hasEndTag,
            segments,
            parts,
            preloadHints,
            renditionReports
        )
    }

    private fun processMediaTag(
        line: String, state: SegmentState, vars: Map<String, String>, baseUri: String,
        parts: MutableList<ParsedUrl>, preloadHints: MutableList<ParsedUrl>, renditionReports: MutableList<ParsedUrl>
    ) {
        when {
            line.startsWith(TAG_MEDIA_SEQUENCE) -> state.mediaSequence = parseLongAttr(line, REGEX_MEDIA_SEQUENCE)
            line.startsWith(TAG_ENDLIST) -> state.hasEndTag = true
            line.startsWith(TAG_MEDIA_DURATION) -> state.durationUs = parseTimeSecondsToUs(line, REGEX_MEDIA_DURATION)
            line.startsWith(TAG_INIT_SEGMENT) -> state.initSegment = parseInitSegment(line, baseUri, vars)
            line.startsWith(TAG_BYTERANGE) -> {
                val range = parseByteRange(line, vars)
                state.length = range.first
                range.second?.let { state.offset = it }
            }

            line.startsWith(TAG_KEY) -> {
                parseOptionalStringAttr(line, REGEX_URI, vars)?.let {
                    state.encryptionKey = ParsedUrl(it, resolveAbsoluteUrl(baseUri, it))
                }
            }

            line.startsWith(TAG_PART) -> {
                parseOptionalStringAttr(line, REGEX_URI, vars)?.let {
                    parts.add(ParsedUrl(it, resolveAbsoluteUrl(baseUri, it)))
                }
            }

            line.startsWith(TAG_PRELOAD_HINT) -> {
                parseOptionalStringAttr(line, REGEX_URI, vars)?.let {
                    preloadHints.add(ParsedUrl(it, resolveAbsoluteUrl(baseUri, it)))
                }
            }

            line.startsWith(TAG_RENDITION_REPORT) -> {
                parseOptionalStringAttr(line, REGEX_URI, vars)?.let {
                    renditionReports.add(ParsedUrl(it, resolveAbsoluteUrl(baseUri, it)))
                }
            }
        }
    }

    private fun parseByteRange(line: String, vars: Map<String, String>): Pair<Long, Long?> {
        val byteRange = parseStringAttr(line, REGEX_BYTERANGE, vars)
        val split = splitString(byteRange, "@")
        return split[0].toLong() to split.getOrNull(1)?.toLong()
    }

    private fun parseInitSegment(line: String, baseUri: String, vars: Map<String, String>): InitializationSegment {
        val uri = parseStringAttr(line, REGEX_URI, vars)
        return InitializationSegment(ParsedUrl(uri, resolveAbsoluteUrl(baseUri, uri)))
    }

    private fun createSegment(
        line: String,
        baseUri: String,
        vars: Map<String, String>,
        state: SegmentState
    ): HlsSegment {
        val uri = replaceVariableReferences(line, vars)
        return HlsSegment(
            url = ParsedUrl(uri, resolveAbsoluteUrl(baseUri, uri)),
            byteRangeOffset = state.offset,
            byteRangeLength = state.length,
            durationUs = state.durationUs,
            initializationSegment = state.initSegment,
            encryptionKey = state.encryptionKey
        )
    }

    private fun parseMultivariantPlaylist(iterator: LineIterator, baseUri: String): HlsPlaylist {
        val vars = mutableMapOf<String, String>()
        val variants = mutableListOf<Variant>()
        val mediaTags = mutableListOf<String>()
        val sessionKeys = mutableListOf<ParsedUrl>()

        while (iterator.hasNext()) {
            val line = iterator.next()
            when {
                line.startsWith(TAG_DEFINE) -> vars[parseStringAttr(line, REGEX_NAME, vars)] = parseStringAttr(line, REGEX_VALUE, vars)
                line.startsWith(TAG_MEDIA) -> mediaTags.add(line)
                line.startsWith(TAG_STREAM_INF) || line.startsWith(TAG_I_FRAME_STREAM_INF) -> variants.add(parseVariant(line, iterator, baseUri, vars))
                line.startsWith(TAG_SESSION_KEY) -> {
                    parseOptionalStringAttr(line, REGEX_URI, vars)?.let {
                        sessionKeys.add(ParsedUrl(it, resolveAbsoluteUrl(baseUri, it)))
                    }
                }
            }
        }

        val renditions = mediaTags.map { parseRendition(it, baseUri, vars) }
        return HlsMultivariantPlaylist(
            baseUri = baseUri, variants = variants,
            videos = renditions.filter { it.type == TYPE_VIDEO }.map { it.rendition },
            audios = renditions.filter { it.type == TYPE_AUDIO }.map { it.rendition },
            subtitles = renditions.filter { it.type == TYPE_SUBTITLES }.map { it.rendition },
            closedCaptions = renditions.filter { it.type == TYPE_CLOSED_CAPTIONS }.map { it.rendition },
            sessionKeyUrls = sessionKeys
        )
    }

    private fun parseVariant(line: String, iterator: LineIterator, base: String, vars: Map<String, String>): Variant {
        val isIFrame = line.startsWith(TAG_I_FRAME_STREAM_INF)
        val uriInManifest = if (isIFrame) parseStringAttr(line, REGEX_URI, vars) else replaceVariableReferences(iterator.next(), vars)
        return Variant(
            url = ParsedUrl(uriInManifest, resolveAbsoluteUrl(base, uriInManifest)),
            videoGroupId = parseOptionalStringAttr(line, REGEX_VIDEO, vars), audioGroupId = parseOptionalStringAttr(line, REGEX_AUDIO, vars),
            subtitleGroupId = parseOptionalStringAttr(line, REGEX_SUBTITLES, vars), captionGroupId = parseOptionalStringAttr(line, REGEX_CLOSED_CAPTIONS, vars),
            isIFrame = isIFrame
        )
    }

    private data class TypedRendition(val type: String, val rendition: Rendition)

    private fun parseRendition(line: String, base: String, vars: Map<String, String>): TypedRendition {
        val type = parseStringAttr(line, REGEX_TYPE, vars)
        val uri = parseOptionalStringAttr(line, REGEX_URI, vars)
        val parsedUrl = uri?.let { ParsedUrl(it, resolveAbsoluteUrl(base, it)) }
        return TypedRendition(type, Rendition(url = parsedUrl, groupId = parseStringAttr(line, REGEX_GROUP_ID, vars), name = parseStringAttr(line, REGEX_NAME, vars)))
    }

    private fun parseTimeSecondsToUs(line: String, regex: Regex): Long {
        val timeValueSeconds = parseStringAttr(line, regex, emptyMap())
        return (timeValueSeconds.toDouble() * MICROS_PER_SECOND).roundToLong()
    }

    private fun checkPlaylistHeader(reader: Reader): Boolean {
        var char = reader.read()
        var isValid = true

        if (char == UTF8_BOM_BYTE_1) {
            val isBom = reader.read() == UTF8_BOM_BYTE_2 && reader.read() == UTF8_BOM_BYTE_3
            if (isBom) {
                char = reader.read()
            } else {
                isValid = false
            }
        }

        if (isValid) {
            char = skipIgnorableWhitespace(reader, skipLinebreak = true, char)
            for (expectedChar in PLAYLIST_HEADER) {
                if (char != expectedChar.code) {
                    isValid = false
                    break
                }
                char = reader.read()
            }
        }

        return if (isValid) {
            val nextChar = skipIgnorableWhitespace(reader, skipLinebreak = false, char)
            isLinebreak(nextChar)
        } else {
            false
        }
    }

    private fun replaceVariableReferences(string: String, vars: Map<String, String>): String =
        REGEX_VARIABLE_REFERENCE.replace(string) { match ->
            vars[match.groupValues[1]]?.let { Regex.escapeReplacement(it) } ?: ""
        }

    private fun parseStringAttr(line: String, regex: Regex, vars: Map<String, String>): String =
        parseOptionalStringAttr(line, regex, null, vars) ?: throw NoSuchElementException("Missing required attribute")

    private fun parseOptionalStringAttr(line: String, regex: Regex, vars: Map<String, String>): String? =
        parseOptionalStringAttr(line, regex, null, vars)

    private fun parseOptionalStringAttr(
        line: String,
        regex: Regex,
        default: String?,
        vars: Map<String, String>
    ): String? {
        val value = regex.find(line)?.groups?.get(1)?.value ?: default
        return if (vars.isEmpty() || value == null) value else replaceVariableReferences(value, vars)
    }

    private fun parseLongAttr(line: String, regex: Regex): Long = parseStringAttr(line, regex, emptyMap()).toLong()
}
