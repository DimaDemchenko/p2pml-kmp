package com.novage.p2pml.internal.parser

import com.novage.p2pml.api.events.Segment
import com.novage.p2pml.internal.parser.hlsPlaylistParser.Stream
import com.novage.p2pml.internal.parser.hlsPlaylistParser.UpdateStreamParams
import kotlin.time.TimeMark

internal class TrackedStreamContext(
    var stream: Stream,
    val segments: MutableMap<Long, Segment> = mutableMapOf(),
    val currentSegmentRuntimeIds: MutableSet<String> = mutableSetOf(),
    var updateParams: UpdateStreamParams? = null,
    var lastUpdated: TimeMark
)
