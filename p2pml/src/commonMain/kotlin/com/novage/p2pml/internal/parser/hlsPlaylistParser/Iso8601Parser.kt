package com.novage.p2pml.internal.parser.hlsPlaylistParser

import kotlin.time.Instant

private const val MILLIS_PER_SECOND = 1000L

internal fun parseIso8601ToUs(dateTime: String): Long? = runCatching { Instant.parse(dateTime.trim()) }
    .getOrNull()
    ?.toEpochMilliseconds()
    ?.times(MICROS_PER_SECOND / MILLIS_PER_SECOND)
