package com.novage.p2pml.internal.playback

import com.novage.p2pml.api.playback.PlaybackInfo
import com.novage.p2pml.internal.parser.TimelineBounds

/**
 * Converts a player-reported position onto the timeline the parser assigned to the segments.
 *
 * The engine compares position against `segment.startTime` and nothing else, so the only thing that
 * matters is that both sit on one timeline. Players do not share the parser's timeline, and on live
 * streams some do not even keep a fixed origin: ExoPlayer measures position from the start of the
 * sliding live window, so its raw value barely changes while playback advances.
 *
 * Rather than reconstructing an absolute time — which requires an anchor, and an anchor captured
 * once goes stale the moment the window slides — this maps the one quantity that survives a moving
 * origin: the distance from the playhead to the live edge. Both terms are measured from the same
 * origin, so the origin cancels out and no anchor is needed.
 */
internal fun mapToStreamTime(info: PlaybackInfo, bounds: TimelineBounds): Double {
    val playerLiveEdge = info.currentLiveEdgePosition
        // On-demand: the player's scale starts where the parser's timeline starts. `start` is 0 for a
        // plain VOD playlist, but is an epoch value when the playlist carries PROGRAM-DATE-TIME, so
        // it cannot be assumed to be zero.
        ?: return bounds.start + info.currentPlayPosition

    val behindLiveEdge = (playerLiveEdge - info.currentPlayPosition).coerceAtLeast(0.0)
    return (bounds.liveEdge - behindLiveEdge).coerceAtLeast(bounds.start)
}
