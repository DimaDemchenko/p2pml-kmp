package com.novage.p2pml.internal.parser.hlsPlaylistParser

import kotlin.time.Instant

private const val MILLIS_PER_SECOND = 1000L

internal fun parseIso8601ToUs(dateTime: String): Long? = try {
    Instant.parse(dateTime.trim())
        .toEpochMilliseconds() * (MICROS_PER_SECOND / MILLIS_PER_SECOND)
} catch (_: IllegalArgumentException) {
    null
}
