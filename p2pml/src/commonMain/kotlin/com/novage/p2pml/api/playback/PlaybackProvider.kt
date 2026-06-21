package com.novage.p2pml.api.playback

import com.novage.p2pml.api.playback.PlaybackInfo

interface PlaybackListener {
    fun onPlaybackInfoUpdated(info: PlaybackInfo)
}

interface PlaybackProvider {
    /**
     * Registers a listener to receive playback progress updates.
     * Pass `null` to unregister and clear any reference to the listener.
     */
    fun setPlaybackListener(listener: PlaybackListener?)

    /**
     * Releases any resources held by this provider.
     * Implementations must clear any listener references here to prevent memory leaks
     * if the provider instance outlives the core session.
     */
    fun release() {}
}
