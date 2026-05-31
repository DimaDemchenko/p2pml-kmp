package com.novage.p2pml.internal.utils

import com.novage.p2pml.api.models.ByteRange

/**
 * Builds the runtime identifier for a segment, encoding the byte range into the URL string.
 * This format must match what the JS engine expects for segment matching.
 *
 * **Single source of truth** — used by both `HlsSegment.runtimeUrl` and
 * `HlsUrlRewriter.rewriteSegmentUrl` to guarantee consistent format.
 */
internal fun buildSegmentRuntimeId(absoluteUrl: String, byteRange: ByteRange?): String =
    byteRange?.let { "$absoluteUrl|${it.start}-${it.end}" } ?: absoluteUrl
