package com.novage.p2pml.api.playback

import kotlinx.serialization.Serializable

/**
 * A snapshot of the player's current playback state, reported to the engine via
 * [PlaybackListener.onPlaybackInfoUpdated].
 *
 * @property currentPlayPosition Current playback position, in seconds.
 * @property currentPlaybackSpeed Current playback speed multiplier (1.0 = normal speed).
 */
@Serializable
data class PlaybackInfo(val currentPlayPosition: Double, val currentPlaybackSpeed: Float)
