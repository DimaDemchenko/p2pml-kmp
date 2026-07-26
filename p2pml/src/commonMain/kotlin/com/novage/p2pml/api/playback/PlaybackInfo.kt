package com.novage.p2pml.api.playback

/**
 * A snapshot of the player's current playback state, reported via
 * [PlaybackListener.onPlaybackInfoUpdated].
 *
 * Both positions are expressed on **the player's own scale**, whatever that happens to be — the
 * library converts them onto the timeline it built from the manifest. Do not translate them to
 * wall-clock time: reporting the player's raw values is both sufficient and what keeps the
 * conversion correct.
 *
 * @property currentPlayPosition Current playback position, in seconds, on the player's own scale.
 * @property currentPlaybackSpeed Current playback speed multiplier (1.0 = normal speed).
 * @property currentLiveEdgePosition Position of the live edge, in seconds, **on the same scale as
 *   [currentPlayPosition]**, or `null` for on-demand content. Only the difference between the two is
 *   used, so a player whose scale slides — one whose origin moves forward as the live window
 *   advances, as ExoPlayer's does — stays correct with no extra bookkeeping.
 */
data class PlaybackInfo(
    val currentPlayPosition: Double,
    val currentPlaybackSpeed: Float,
    val currentLiveEdgePosition: Double? = null
)
