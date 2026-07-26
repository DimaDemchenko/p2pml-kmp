package com.novage.p2pml.internal.parser

/**
 * The extent of the timeline the parser has assigned to the main stream, in the same units as
 * [com.novage.p2pml.api.events.Segment.startTime].
 *
 * [start] is the earliest tracked segment start and [liveEdge] the latest tracked segment end. Both
 * advance as a live playlist slides; for VOD they are fixed. This is the timeline the engine
 * compares playback position against, so a reported position is only meaningful once expressed
 * relative to these bounds.
 */
internal data class TimelineBounds(val start: Double, val liveEdge: Double)
