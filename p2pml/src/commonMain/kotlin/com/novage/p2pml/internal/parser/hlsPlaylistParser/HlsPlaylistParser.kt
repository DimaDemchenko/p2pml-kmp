package com.novage.p2pml.internal.parser.hlsPlaylistParser

import com.novage.p2pml.internal.parser.ManifestParser
import com.novage.p2pml.internal.utils.CoreLogger
import kotlin.math.roundToLong

private const val VARIABLE_REFERENCE_MARKER = "{\$"
private const val BYTERANGE_SEPARATOR = '@'

private data class ParserContext(val baseUri: String, val vars: MutableMap<String, String> = mutableMapOf())

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

private class LowLatencyState(
    val parts: MutableList<ParsedUrl> = mutableListOf(),
    val preloadHints: MutableList<ParsedUrl> = mutableListOf(),
    val renditionReports: MutableList<ParsedUrl> = mutableListOf()
)

internal class HlsPlaylistParser(
    private val urlRewriter: HlsUrlRewriter,
    private val logger: CoreLogger = CoreLogger("HlsPlaylistParser")
) : ManifestParser<ParsedPlaylist> {

    internal class PlaylistLineIterator(linesSequence: Sequence<String>) : Iterator<String> {
        private val iterator = linesSequence.iterator()
        private val extraLines = ArrayDeque<String>()

        override fun hasNext(): Boolean {
            if (extraLines.isNotEmpty()) return true
            return iterator.hasNext()
        }

        override fun next(): String {
            if (!hasNext()) {
                throw NoSuchElementException("No more lines")
            }
            if (extraLines.isNotEmpty()) {
                return extraLines.removeFirst()
            }
            return iterator.next()
        }

        fun pushBack(line: String) {
            extraLines.addFirst(line)
        }
    }

    override fun parse(manifestUrl: String, manifestData: String): ParsedPlaylist {
        val cleaned = cleanBOM(manifestData)
        val iterator = PlaylistLineIterator(cleaned.lineSequence())
        val context = ParserContext(baseUri = manifestUrl)

        var headerLine: String? = null
        while (iterator.hasNext()) {
            val line = iterator.next()
            if (line.trim().isNotEmpty()) {
                headerLine = line
                break
            }
        }

        require(headerLine != null && headerLine.trim() == PLAYLIST_HEADER) {
            logger.e { "Playlist missing $PLAYLIST_HEADER header: $manifestUrl" }
            "Invalid playlist header"
        }

        val extraLines = ArrayDeque<String>()
        while (iterator.hasNext()) {
            val line = iterator.next()
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            extraLines.add(line)
            val result = checkLineForPlaylistType(trimmed, extraLines, iterator, context)
            if (result != null) return result
        }

        error("Failed to parse playlist: No valid tags found")
    }

    private fun checkLineForPlaylistType(
        trimmed: String,
        extraLines: ArrayDeque<String>,
        iterator: PlaylistLineIterator,
        context: ParserContext
    ): ParsedPlaylist? = when {
        trimmed.startsWith(TAG_STREAM_INF) -> {
            pushBackExtraLines(extraLines, iterator)
            parseMultivariantPlaylist(iterator, context)
        }

        isMediaPlaylistTag(trimmed) -> {
            pushBackExtraLines(extraLines, iterator)
            parseMediaPlaylist(iterator, context)
        }

        else -> null
    }

    private fun pushBackExtraLines(extraLines: ArrayDeque<String>, iterator: PlaylistLineIterator) {
        while (extraLines.isNotEmpty()) {
            iterator.pushBack(extraLines.removeLast())
        }
    }

    private fun parseMediaPlaylist(iterator: PlaylistLineIterator, context: ParserContext): ParsedPlaylist {
        val segmentState = SegmentState()
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
                rewrittenLine = processMediaTag(originalLine, trimmed, segmentState, llState, context)
            } else {
                val seg = createSegment(trimmed, context.baseUri, context.vars, segmentState)
                segments.add(seg)
                urlRewriter.rewriteSegmentUrl(seg.url, seg.byteRange).let { newUrl ->
                    rewrittenLine = originalLine.replaceFirst(trimmed, newUrl)
                }
                if (segmentState.length != -1L) segmentState.offset += segmentState.length
                segmentState.durationUs = 0L
                segmentState.length = -1L
                segmentState.programDateTimeUs = null
            }
            builder.append(rewrittenLine).append("\n")
        }

        val playlist = HlsMediaPlaylist(
            context.baseUri,
            segmentState.mediaSequence,
            segmentState.hasEndTag,
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
    ): String = when {
        trimmedLine.startsWith(TAG_DEFINE) -> {
            parseDefineTag(trimmedLine, context)
            originalLine
        }

        trimmedLine.startsWith(TAG_MEDIA_SEQUENCE) -> {
            parseMediaSequenceTag(trimmedLine, state)
            originalLine
        }

        trimmedLine.startsWith(TAG_ENDLIST) -> {
            state.hasEndTag = true
            originalLine
        }

        trimmedLine.startsWith(TAG_MEDIA_DURATION) -> {
            parseMediaDurationTag(trimmedLine, state)
            originalLine
        }

        trimmedLine.startsWith(TAG_BYTERANGE) -> {
            applyByteRange(trimmedLine, context.vars, state)
            originalLine
        }

        trimmedLine.startsWith(TAG_PROGRAM_DATE_TIME) -> {
            parseProgramDateTimeTag(trimmedLine, state)
            originalLine
        }

        trimmedLine.startsWith(TAG_INIT_SEGMENT) -> {
            processInitSegmentTag(originalLine, trimmedLine, state, context)
        }

        trimmedLine.startsWith(TAG_KEY) -> {
            processKeyTag(originalLine, trimmedLine, state, context)
        }

        trimmedLine.startsWith(TAG_PART) -> {
            processPartTag(originalLine, trimmedLine, llState, context)
        }

        trimmedLine.startsWith(TAG_PRELOAD_HINT) -> {
            processPreloadHintTag(originalLine, trimmedLine, llState, context)
        }

        trimmedLine.startsWith(TAG_RENDITION_REPORT) -> {
            processRenditionReportTag(originalLine, trimmedLine, llState, context)
        }

        else -> originalLine
    }

    private fun processInitSegmentTag(
        originalLine: String,
        trimmedLine: String,
        state: SegmentState,
        context: ParserContext
    ): String = processUrlTag(
        line = originalLine,
        urlExtractor = {
            parseUrlAttribute(trimmedLine, context.vars, context.baseUri)
                ?: throw NoSuchElementException("Missing URI")
        },
        stateUpdater = { state.initSegment = InitializationSegment(it) },
        rewriter = { urlRewriter.rewriteInitSegmentUrl(it) }
    )

    private fun processPartTag(
        originalLine: String,
        trimmedLine: String,
        llState: LowLatencyState,
        context: ParserContext
    ): String = processUrlTag(
        line = originalLine,
        urlExtractor = { parseUrlAttribute(trimmedLine, context.vars, context.baseUri) },
        stateUpdater = { llState.parts.add(it) },
        rewriter = { urlRewriter.rewriteLowLatencyUrl(it) }
    )

    private fun processPreloadHintTag(
        originalLine: String,
        trimmedLine: String,
        llState: LowLatencyState,
        context: ParserContext
    ): String = processUrlTag(
        line = originalLine,
        urlExtractor = { parseUrlAttribute(trimmedLine, context.vars, context.baseUri) },
        stateUpdater = { llState.preloadHints.add(it) },
        rewriter = { urlRewriter.rewriteLowLatencyUrl(it) }
    )

    private fun processRenditionReportTag(
        originalLine: String,
        trimmedLine: String,
        llState: LowLatencyState,
        context: ParserContext
    ): String = processUrlTag(
        line = originalLine,
        urlExtractor = { parseUrlAttribute(trimmedLine, context.vars, context.baseUri) },
        stateUpdater = { llState.renditionReports.add(it) },
        rewriter = { urlRewriter.rewriteLowLatencyUrl(it) }
    )

    private fun processKeyTag(
        originalLine: String,
        trimmedLine: String,
        state: SegmentState,
        context: ParserContext
    ): String {
        val parsedUrl = parseUrlAttribute(trimmedLine, context.vars, context.baseUri)
        state.encryptionKey = parsedUrl
        return parsedUrl?.let { url ->
            urlRewriter.rewriteKeyUrl(url).let { newUrl ->
                rewriteUriAttribute(originalLine, url.original, newUrl)
            }
        } ?: originalLine
    }

    private inline fun processUrlTag(
        line: String,
        urlExtractor: () -> ParsedUrl?,
        stateUpdater: (ParsedUrl) -> Unit,
        rewriter: (ParsedUrl) -> String
    ): String {
        val parsedUrl = urlExtractor() ?: return line

        stateUpdater(parsedUrl)

        val newUrl = rewriter(parsedUrl)
        return rewriteUriAttribute(line, parsedUrl.original, newUrl)
    }

    private fun parseMultivariantPlaylist(iterator: PlaylistLineIterator, context: ParserContext): ParsedPlaylist {
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
                    parseDefineTag(trimmedLine, context)
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
        iterator: PlaylistLineIterator,
        context: ParserContext,
        variants: MutableList<Variant>
    ): String {
        val isIFrame = trimmedLine.startsWith(TAG_I_FRAME_STREAM_INF)
        if (isIFrame) {
            val variant = parseVariant(trimmedLine, context, null, true)
            variants.add(variant)
            return urlRewriter.rewriteVariantUrl(variant.url, true).let { newUrl ->
                rewriteUriAttribute(originalLine, variant.url.original, newUrl)
            }
        }

        val skippedEmptyLines = StringBuilder()
        var nextOriginalLine = ""
        var nextTrimmedLine = ""
        var foundUri = false
        while (iterator.hasNext()) {
            val line = iterator.next()
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                skippedEmptyLines.append(line).append("\n")
            } else {
                nextOriginalLine = line
                nextTrimmedLine = trimmed
                foundUri = true
                break
            }
        }
        check(foundUri) { "Missing URI line after $TAG_STREAM_INF" }

        val variant = parseVariant(trimmedLine, context, nextTrimmedLine, false)
        variants.add(variant)

        val rewrittenNextLine = urlRewriter.rewriteVariantUrl(variant.url, false).let { newUrl ->
            nextOriginalLine.replaceFirst(variant.url.original, newUrl)
        }

        return if (skippedEmptyLines.isNotEmpty()) {
            "$originalLine\n$skippedEmptyLines$rewrittenNextLine"
        } else {
            "$originalLine\n$rewrittenNextLine"
        }
    }

    private fun processMediaRendition(
        originalLine: String,
        trimmedLine: String,
        context: ParserContext,
        renditions: MutableList<TypedRendition>
    ): String {
        val typedRend = parseRendition(trimmedLine, context.baseUri, context.vars)
        renditions.add(typedRend)

        val url = typedRend.rendition.url ?: return originalLine
        val newUrl = urlRewriter.rewriteRenditionUrl(url, typedRend.type)

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

        val newUrl = urlRewriter.rewriteSessionKeyUrl(parsedUrl)

        return rewriteUriAttribute(originalLine, parsedUrl.original, newUrl)
    }
}

private fun cleanBOM(data: String): String = if (data.startsWith("\uFEFF")) data.substring(1) else data

private fun isMediaPlaylistTag(trimmed: String): Boolean = trimmed.startsWith(TAG_TARGET_DURATION) ||
    trimmed.startsWith(TAG_MEDIA_SEQUENCE) ||
    trimmed.startsWith(TAG_MEDIA_DURATION) ||
    trimmed.startsWith(TAG_KEY) ||
    trimmed.startsWith(TAG_BYTERANGE) ||
    trimmed == TAG_DISCONTINUITY ||
    trimmed == TAG_DISCONTINUITY_SEQUENCE ||
    trimmed == TAG_ENDLIST

private fun parseDefineTag(line: String, context: ParserContext) {
    val name = parseStringAttr(line, REGEX_NAME, context.vars)
    val value = parseStringAttr(line, REGEX_VALUE, context.vars)
    context.vars[name] = value
}

private fun parseMediaSequenceTag(line: String, state: SegmentState) {
    state.mediaSequence = parseLongAttr(line, REGEX_MEDIA_SEQUENCE)
}

private fun parseMediaDurationTag(line: String, state: SegmentState) {
    state.durationUs = parseTimeSecondsToUs(line, REGEX_MEDIA_DURATION)
}

private fun parseProgramDateTimeTag(line: String, state: SegmentState) {
    val dateString = line.substringAfter(":")
    state.programDateTimeUs = parseIso8601ToUs(dateString)
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

private fun createSegment(line: String, baseUri: String, vars: Map<String, String>, state: SegmentState): HlsSegment =
    HlsSegment(
        url = line.toParsedUrl(baseUri, vars),
        byteRangeOffset = state.offset,
        byteRangeLength = state.length,
        durationUs = state.durationUs,
        programDateTimeUs = state.programDateTimeUs,
        initializationSegment = state.initSegment,
        encryptionKey = state.encryptionKey
    )

private fun parseVariant(line: String, context: ParserContext, nextLine: String? = null, isIFrame: Boolean): Variant {
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
    parseOptionalStringAttr(line, regex, vars) ?: throw NoSuchElementException("Missing required attribute in line: ${line.take(100)}")

private fun parseOptionalStringAttr(
    line: String,
    regex: Regex,
    vars: Map<String, String>,
    default: String? = null
): String? = (regex.find(line)?.groups?.get(1)?.value ?: default)?.let { replaceVariableReferences(it, vars) }

private fun parseLongAttr(line: String, regex: Regex): Long = parseStringAttr(line, regex, emptyMap()).toLong()
