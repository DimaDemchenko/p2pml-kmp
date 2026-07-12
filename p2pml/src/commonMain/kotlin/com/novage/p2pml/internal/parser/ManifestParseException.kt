package com.novage.p2pml.internal.parser

/**
 * Thrown when upstream content cannot be parsed as a valid HLS playlist — e.g. an origin/CDN
 * serving an HTML error page with HTTP 200, or a manifest with malformed tags. Wraps the
 * low-level failure ([IllegalArgumentException], [IllegalStateException],
 * [NoSuchElementException], [NumberFormatException]) so route handlers deal with one type.
 */
internal class ManifestParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
