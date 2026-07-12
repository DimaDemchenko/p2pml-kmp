package com.novage.p2pml.internal.parser

import com.novage.p2pml.api.events.ByteRange

private const val BYTE_RANGE_DELIMITER = '|'

/**
 * Builds the runtime identifier for a segment, encoding the byte range into the URL string.
 * This format must match what the JS engine expects for segment matching.
 *
 * **Single source of truth** for the runtime-id format — used by both `HlsSegment.runtimeUrl` and
 * `HlsUrlRewriter.rewriteSegmentUrl` to encode, and by [segmentUrlFromRuntimeId] to decode.
 */
internal fun buildSegmentRuntimeId(absoluteUrl: String, byteRange: ByteRange?): String =
    byteRange?.let { "$absoluteUrl$BYTE_RANGE_DELIMITER${it.start}-${it.end}" } ?: absoluteUrl

/**
 * Recovers the absolute segment URL from a runtime id produced by [buildSegmentRuntimeId],
 * dropping any encoded byte range. Returns the input unchanged when no byte range is present.
 */
internal fun segmentUrlFromRuntimeId(runtimeId: String): String = BYTE_RANGE_SUFFIX.replace(runtimeId, "")

private val BYTE_RANGE_SUFFIX = Regex("""\|(\d+)-(\d+)$""")

/** Extracts the byte range encoded by [buildSegmentRuntimeId], or null for whole-segment ids. */
internal fun byteRangeFromRuntimeId(runtimeId: String): ByteRange? = BYTE_RANGE_SUFFIX.find(runtimeId)?.let { match ->
    ByteRange(match.groupValues[1].toLong(), match.groupValues[2].toLong())
}
