package com.novage.p2pml.api.models

import kotlinx.serialization.Serializable

/**
 * Playback info
 *
 * @param currentPlayPosition current play position in seconds
 * @param currentPlaybackSpeed current play speed
 */
@Serializable
data class PlaybackInfo(val currentPlayPosition: Double, val currentPlaybackSpeed: Float)
