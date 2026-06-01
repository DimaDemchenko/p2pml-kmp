package com.novage.p2pml.internal.parser.hlsPlaylistParser

import com.novage.p2pml.internal.utils.CoreLogger
import kotlin.time.Instant

private const val MILLIS_PER_SECOND = 1000L
private val logger = CoreLogger("Iso8601Parser")

internal fun parseIso8601ToUs(dateTime: String): Long? = try {
    Instant.parse(dateTime.trim())
        .toEpochMilliseconds() * (MICROS_PER_SECOND / MILLIS_PER_SECOND)
} catch (_: IllegalArgumentException) {
    logger.w { "Failed to parse ISO 8601 date: $dateTime" }
    null
}
