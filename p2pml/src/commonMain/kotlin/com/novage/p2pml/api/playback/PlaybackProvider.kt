package com.novage.p2pml.api.playback

/**
 * Receives playback progress updates from a [PlaybackProvider]. The engine uses these to align P2P
 * segment prioritization with the current playback position.
 */
interface PlaybackListener {
    /**
     * Called when the player's position or speed changes. Expected to fire frequently (roughly once
     * per playback tick / second), so keep the implementation cheap.
     */
    fun onPlaybackInfoUpdated(info: PlaybackInfo)
}

/**
 * Bridges a media player to the P2P engine, reporting playback progress via a [PlaybackListener] so
 * the engine knows what to prioritize. The library ships providers for ExoPlayer (Android) and
 * AVPlayer (iOS); implement this to integrate a custom player.
 */
interface PlaybackProvider {
    /**
     * Registers a listener to receive playback progress updates.
     * Pass `null` to unregister and clear any reference to the listener.
     */
    fun setPlaybackListener(listener: PlaybackListener?)

    /**
     * Releases any resources held by this provider. The default implementation does nothing,
     * which is only correct for providers that hold no state of their own.
     *
     * Override this if the provider retains the [PlaybackListener], player callbacks, timers or
     * similar, and clear them here so a provider instance that outlives the core session does
     * not leak.
     */
    fun release() {}
}
