package com.novage.p2pml.internal.parser.hlsPlaylistParser

import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.MICROS_PER_SECOND
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.PLAYLIST_HEADER
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_AUDIO
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_AVERAGE_BANDWIDTH
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_BANDWIDTH
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_BYTERANGE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_CHANNELS
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_CLOSED_CAPTIONS
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_CODECS
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_FRAME_RATE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_GROUP_ID
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_LANGUAGE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_MEDIA_DURATION
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_MEDIA_SEQUENCE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_NAME
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_RESOLUTION
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_SUBTITLES
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_TYPE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_URI
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_VALUE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_VARIABLE_REFERENCE
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_VIDEO
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.REGEX_VIDEO_RANGE
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
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsConstants.TAG_PROGRAM_DATE_TIME
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

private const val VARIABLE_REFERENCE_MARKER = "{\$"
private const val BYTERANGE_SEPARATOR = '@'

internal class HlsPlaylistParser {
    private val logger = CoreLogger("HlsPlaylistParser")

    internal data class ParserContext(
        val baseUri: String,
        val vars: MutableMap<String, String> = mutableMapOf(),
        val urlRewriter: HlsUrlRewriter? = null
    )

    private data class SegmentState(
        var mediaSequence: Long = 0L,
        var hasEndTag: Boolean = false,
        var durationUs: Long = 0L,
        var offset: Long = 0L,
        var length: Long = -1L,
        var programDateTimeUs: Long? = null,
        var initSegment: InitializationSegment? = null,
        var encryptionKey: ParsedUrl? = null
    )

    fun parse(playlistUri: String, playlistData: String, urlRewriter: HlsUrlRewriter? = null): ParsedPlaylist {
        val reader = Reader(playlistData)
        val extraLines = ArrayDeque<String>()
        val context = ParserContext(baseUri = playlistUri, urlRewriter = urlRewriter)

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
                        return parseMultivariantPlaylist(LineIterator(extraLines, reader), context)

                    isMediaPlaylistTag(trimmed) ->
                        return parseMediaPlaylist(LineIterator(extraLines, reader), context)
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

    private class LowLatencyState(
        val parts: MutableList<ParsedUrl> = mutableListOf(),
        val preloadHints: MutableList<ParsedUrl> = mutableListOf(),
        val renditionReports: MutableList<ParsedUrl> = mutableListOf()
    )

    private fun parseMediaPlaylist(iterator: LineIterator, context: ParserContext): ParsedPlaylist {
        val state = SegmentState()
        val llState = LowLatencyState()
        val segments = mutableListOf<HlsSegment>()
        val builder = StringBuilder("$PLAYLIST_HEADER\n")

        while (iterator.hasNext()) {
            val originalLine = iterator.next()
            val trimmed = originalLine.trim()
            if (trimmed.isEmpty()) {
                builder.append(originalLine).append("\n")
                continue
            }

            var rewrittenLine = originalLine
            if (trimmed.startsWith("#")) {
                rewrittenLine = processMediaTag(originalLine, trimmed, state, llState, context)
            } else {
                val seg = createSegment(trimmed, context.baseUri, context.vars, state)
                segments.add(seg)
                context.urlRewriter?.rewriteSegmentUrl(seg.url, seg.byteRange)?.let { newUrl ->
                    rewrittenLine = originalLine.replaceFirst(trimmed, newUrl)
                }
                if (state.length != -1L) state.offset += state.length
                state.durationUs = 0L
                state.length = -1L
                state.programDateTimeUs = null
            }
            builder.append(rewrittenLine).append("\n")
        }

        val playlist = HlsMediaPlaylist(
            context.baseUri,
            state.mediaSequence,
            state.hasEndTag,
            segments,
            llState.parts,
            llState.preloadHints,
            llState.renditionReports
        )
        return ParsedPlaylist(playlist, builder.toString())
    }

    private fun processMediaTag(
        originalLine: String,
        trimmedLine: String,
        state: SegmentState,
        llState: LowLatencyState,
        context: ParserContext
    ): String {
        var rewrittenLine = originalLine

        when {
            trimmedLine.startsWith(TAG_DEFINE) -> {
                context.vars[parseStringAttr(trimmedLine, REGEX_NAME, context.vars)] =
                    parseStringAttr(trimmedLine, REGEX_VALUE, context.vars)
            }

            trimmedLine.startsWith(TAG_MEDIA_SEQUENCE) ->
                state.mediaSequence =
                    parseLongAttr(trimmedLine, REGEX_MEDIA_SEQUENCE)

            trimmedLine.startsWith(TAG_ENDLIST) -> state.hasEndTag = true

            trimmedLine.startsWith(TAG_MEDIA_DURATION) ->
                state.durationUs =
                    parseTimeSecondsToUs(trimmedLine, REGEX_MEDIA_DURATION)

            trimmedLine.startsWith(TAG_BYTERANGE) -> applyByteRange(trimmedLine, context.vars, state)

            trimmedLine.startsWith(TAG_PROGRAM_DATE_TIME) -> {
                val dateString = trimmedLine.substringAfter(":")
                state.programDateTimeUs = parseIso8601ToUs(dateString)
            }

            trimmedLine.startsWith(TAG_INIT_SEGMENT) -> rewrittenLine = processUrlTag(
                line = originalLine,
                urlExtractor = {
                    parseUrlAttribute(trimmedLine, context.vars, context.baseUri)
                        ?: throw NoSuchElementException("Missing URI")
                },
                stateUpdater = { state.initSegment = InitializationSegment(it) },
                rewriter = { context.urlRewriter?.rewriteInitSegmentUrl(it) }
            )

            trimmedLine.startsWith(TAG_KEY) ->
                rewrittenLine = processKeyTag(originalLine, trimmedLine, state, context)

            trimmedLine.startsWith(TAG_PART) -> rewrittenLine = processUrlTag(
                line = originalLine,
                urlExtractor = { parseUrlAttribute(trimmedLine, context.vars, context.baseUri) },
                stateUpdater = { llState.parts.add(it) },
                rewriter = { context.urlRewriter?.rewriteLowLatencyUrl(it) }
            )

            trimmedLine.startsWith(TAG_PRELOAD_HINT) -> rewrittenLine = processUrlTag(
                line = originalLine,
                urlExtractor = { parseUrlAttribute(trimmedLine, context.vars, context.baseUri) },
                stateUpdater = { llState.preloadHints.add(it) },
                rewriter = { context.urlRewriter?.rewriteLowLatencyUrl(it) }
            )

            trimmedLine.startsWith(TAG_RENDITION_REPORT) -> rewrittenLine = processUrlTag(
                line = originalLine,
                urlExtractor = { parseUrlAttribute(trimmedLine, context.vars, context.baseUri) },
                stateUpdater = { llState.renditionReports.add(it) },
                rewriter = { context.urlRewriter?.rewriteLowLatencyUrl(it) }
            )
        }
        return rewrittenLine
    }

    private fun processKeyTag(
        originalLine: String,
        trimmedLine: String,
        state: SegmentState,
        context: ParserContext
    ): String {
        val parsedUrl = parseUrlAttribute(trimmedLine, context.vars, context.baseUri)
        state.encryptionKey = parsedUrl
        return parsedUrl?.let { url ->
            context.urlRewriter?.rewriteKeyUrl(url)?.let { newUrl ->
                rewriteUriAttribute(originalLine, url.original, newUrl)
            }
        } ?: originalLine
    }

    private inline fun processUrlTag(
        line: String,
        urlExtractor: () -> ParsedUrl?,
        stateUpdater: (ParsedUrl) -> Unit,
        rewriter: (ParsedUrl) -> String?
    ): String {
        val parsedUrl = urlExtractor() ?: return line

        stateUpdater(parsedUrl)

        return rewriter(parsedUrl)?.let { newUrl ->
            rewriteUriAttribute(line, parsedUrl.original, newUrl)
        } ?: line
    }

    private fun rewriteUriAttribute(line: String, originalUrl: String, newUrl: String): String {
        val uriWithQuotes = "URI=\"$originalUrl\""
        if (line.contains(uriWithQuotes)) {
            return line.replaceFirst(uriWithQuotes, "URI=\"$newUrl\"")
        }

        val uriWithoutQuotes = "URI=$originalUrl"
        if (line.contains(uriWithoutQuotes)) {
            return line.replaceFirst(uriWithoutQuotes, "URI=$newUrl")
        }

        return line.replaceFirst(originalUrl, newUrl)
    }

    private fun applyByteRange(line: String, vars: Map<String, String>, state: SegmentState) {
        val byteRange = parseStringAttr(line, REGEX_BYTERANGE, vars)
        val split = byteRange.split(BYTERANGE_SEPARATOR)
        state.length = split[0].toLong()
        split.getOrNull(1)?.toLong()?.let { state.offset = it }
    }

    private fun String.toParsedUrl(baseUri: String, vars: Map<String, String>): ParsedUrl {
        val expanded = replaceVariableReferences(this, vars)
        return ParsedUrl(this, resolveAbsoluteUrl(baseUri, expanded))
    }

    private fun parseUrlAttribute(line: String, vars: Map<String, String>, baseUri: String): ParsedUrl? =
        REGEX_URI.find(line)?.groups?.get(1)?.value?.toParsedUrl(baseUri, vars)

    private fun createSegment(
        line: String,
        baseUri: String,
        vars: Map<String, String>,
        state: SegmentState
    ): HlsSegment = HlsSegment(
        url = line.toParsedUrl(baseUri, vars),
        byteRangeOffset = state.offset,
        byteRangeLength = state.length,
        durationUs = state.durationUs,
        programDateTimeUs = state.programDateTimeUs,
        initializationSegment = state.initSegment,
        encryptionKey = state.encryptionKey
    )

    private fun parseMultivariantPlaylist(iterator: LineIterator, context: ParserContext): ParsedPlaylist {
        val variants = mutableListOf<Variant>()
        val renditions = mutableListOf<TypedRendition>()
        val sessionKeys = mutableListOf<ParsedUrl>()
        val builder = StringBuilder("$PLAYLIST_HEADER\n")

        while (iterator.hasNext()) {
            val originalLine = iterator.next()
            val trimmedLine = originalLine.trim()
            if (trimmedLine.isEmpty()) {
                builder.append(originalLine).append("\n")
                continue
            }

            var rewrittenLine = originalLine
            when {
                trimmedLine.startsWith(TAG_DEFINE) -> {
                    context.vars[parseStringAttr(trimmedLine, REGEX_NAME, context.vars)] =
                        parseStringAttr(trimmedLine, REGEX_VALUE, context.vars)
                }

                trimmedLine.startsWith(TAG_MEDIA) -> {
                    rewrittenLine = processMediaRendition(originalLine, trimmedLine, context, renditions)
                }

                trimmedLine.startsWith(TAG_STREAM_INF) || trimmedLine.startsWith(TAG_I_FRAME_STREAM_INF) -> {
                    rewrittenLine = parseAndRewriteVariant(originalLine, trimmedLine, iterator, context, variants)
                }

                trimmedLine.startsWith(TAG_SESSION_KEY) -> {
                    rewrittenLine = processSessionKeyLine(originalLine, trimmedLine, context, sessionKeys)
                }
            }
            builder.append(rewrittenLine).append("\n")
        }

        val playlist = HlsMultivariantPlaylist(
            baseUri = context.baseUri,
            variants = variants,
            videos = renditions.filter { it.type == TYPE_VIDEO }.map { it.rendition },
            audios = renditions.filter { it.type == TYPE_AUDIO }.map { it.rendition },
            subtitles = renditions.filter { it.type == TYPE_SUBTITLES }.map { it.rendition },
            closedCaptions = renditions.filter { it.type == TYPE_CLOSED_CAPTIONS }.map { it.rendition },
            sessionKeyUrls = sessionKeys
        )
        return ParsedPlaylist(playlist, builder.toString())
    }

    private fun parseAndRewriteVariant(
        originalLine: String,
        trimmedLine: String,
        iterator: LineIterator,
        context: ParserContext,
        variants: MutableList<Variant>
    ): String {
        val isIFrame = trimmedLine.startsWith(TAG_I_FRAME_STREAM_INF)
        if (isIFrame) {
            val variant = parseVariant(trimmedLine, context, null, true)
            variants.add(variant)
            return context.urlRewriter?.rewriteVariantUrl(variant.url, true)?.let { newUrl ->
                rewriteUriAttribute(originalLine, variant.url.original, newUrl)
            } ?: originalLine
        }

        check(iterator.hasNext()) { "Missing URI line after $TAG_STREAM_INF" }
        val nextOriginalLine = iterator.next()
        val nextTrimmedLine = nextOriginalLine.trim()
        val variant = parseVariant(trimmedLine, context, nextTrimmedLine, false)
        variants.add(variant)

        val rewrittenNextLine = context.urlRewriter?.rewriteVariantUrl(variant.url, false)?.let { newUrl ->
            nextOriginalLine.replaceFirst(variant.url.original, newUrl)
        } ?: nextOriginalLine

        return "$originalLine\n$rewrittenNextLine"
    }

    private fun parseVariant(
        line: String,
        context: ParserContext,
        nextLine: String? = null,
        isIFrame: Boolean
    ): Variant {
        val parsedUrl = if (isIFrame) {
            parseUrlAttribute(line, context.vars, context.baseUri)
                ?: throw NoSuchElementException("Missing URI for I-FRAME variant")
        } else {
            requireNotNull(nextLine) { "Missing URI line for variant" }
                .trim()
                .toParsedUrl(context.baseUri, context.vars)
        }

        val resolution = parseOptionalStringAttr(line, REGEX_RESOLUTION, context.vars)

        return Variant(
            url = parsedUrl,
            videoGroupId = parseOptionalStringAttr(line, REGEX_VIDEO, context.vars),
            audioGroupId = parseOptionalStringAttr(line, REGEX_AUDIO, context.vars),
            subtitleGroupId = parseOptionalStringAttr(line, REGEX_SUBTITLES, context.vars),
            captionGroupId = parseOptionalStringAttr(line, REGEX_CLOSED_CAPTIONS, context.vars),
            isIFrame = isIFrame,
            bandwidth = parseOptionalStringAttr(line, REGEX_BANDWIDTH, context.vars)?.toIntOrNull(),
            averageBandwidth = parseOptionalStringAttr(line, REGEX_AVERAGE_BANDWIDTH, context.vars)?.toIntOrNull(),
            codecs = parseOptionalStringAttr(line, REGEX_CODECS, context.vars),
            width = resolution?.substringBefore("x")?.toIntOrNull(),
            height = resolution?.substringAfter("x")?.toIntOrNull(),
            frameRate = parseOptionalStringAttr(line, REGEX_FRAME_RATE, context.vars),
            videoRange = parseOptionalStringAttr(line, REGEX_VIDEO_RANGE, context.vars)
        )
    }

    private data class TypedRendition(val type: String, val rendition: Rendition)

    private fun parseRendition(line: String, base: String, vars: Map<String, String>): TypedRendition {
        val type = parseStringAttr(line, REGEX_TYPE, vars)
        val parsedUrl = parseUrlAttribute(line, vars, base)
        return TypedRendition(
            type,
            Rendition(
                url = parsedUrl,
                groupId = parseStringAttr(line, REGEX_GROUP_ID, vars),
                name = parseStringAttr(line, REGEX_NAME, vars),
                language = parseOptionalStringAttr(line, REGEX_LANGUAGE, vars),
                channels = parseOptionalStringAttr(line, REGEX_CHANNELS, vars)
            )
        )
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
            if (isBom) char = reader.read() else isValid = false
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

        return isValid && isLinebreak(skipIgnorableWhitespace(reader, skipLinebreak = false, char))
    }

    private fun replaceVariableReferences(string: String, vars: Map<String, String>): String {
        val containsNoVariables = vars.isEmpty() || string.isEmpty() || !string.contains(VARIABLE_REFERENCE_MARKER)

        return if (containsNoVariables) {
            string
        } else {
            REGEX_VARIABLE_REFERENCE.replace(string) { match ->
                vars[match.groupValues[1]]?.let { Regex.escapeReplacement(it) } ?: ""
            }
        }
    }

    private fun parseStringAttr(line: String, regex: Regex, vars: Map<String, String>): String =
        parseOptionalStringAttr(line, regex, vars) ?: throw NoSuchElementException("Missing required attribute")

    private fun parseOptionalStringAttr(
        line: String,
        regex: Regex,
        vars: Map<String, String>,
        default: String? = null
    ): String? = (regex.find(line)?.groups?.get(1)?.value ?: default)?.let { replaceVariableReferences(it, vars) }

    private fun parseLongAttr(line: String, regex: Regex): Long = parseStringAttr(line, regex, emptyMap()).toLong()

    private fun processMediaRendition(
        originalLine: String,
        trimmedLine: String,
        context: ParserContext,
        renditions: MutableList<TypedRendition>
    ): String {
        val typedRend = parseRendition(trimmedLine, context.baseUri, context.vars)
        renditions.add(typedRend)

        val url = typedRend.rendition.url ?: return originalLine
        val newUrl = context.urlRewriter?.rewriteRenditionUrl(url, typedRend.type) ?: return originalLine

        return rewriteUriAttribute(originalLine, url.original, newUrl)
    }

    private fun processSessionKeyLine(
        originalLine: String,
        trimmedLine: String,
        context: ParserContext,
        sessionKeys: MutableList<ParsedUrl>
    ): String {
        val parsedUrl = parseUrlAttribute(trimmedLine, context.vars, context.baseUri) ?: return originalLine
        sessionKeys.add(parsedUrl)

        val newUrl = context.urlRewriter?.rewriteSessionKeyUrl(parsedUrl) ?: return originalLine

        return rewriteUriAttribute(originalLine, parsedUrl.original, newUrl)
    }
}
