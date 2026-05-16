package com.novage.p2pml.internal.parser

import com.novage.p2pml.api.models.Segment
import com.novage.p2pml.internal.parser.hlsPlaylistParser.Stream
import com.novage.p2pml.internal.parser.hlsPlaylistParser.UpdateStreamParams
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class TrackedStreamContext(
    var stream: Stream,
    val segments: MutableMap<Long, Segment> = mutableMapOf(),
    val currentSegmentRuntimeIds: MutableSet<String> = mutableSetOf(),
    var updateParams: UpdateStreamParams? = null,
    var lastUpdated: TimeMark = TimeSource.Monotonic.markNow()
)
