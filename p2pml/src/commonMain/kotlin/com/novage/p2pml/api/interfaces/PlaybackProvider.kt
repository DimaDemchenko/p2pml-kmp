package com.novage.p2pml.api.interfaces

import com.novage.p2pml.api.models.PlaybackInfo

interface PlaybackListener {
    fun onPlaybackInfoUpdated(info: PlaybackInfo)
}

interface PlaybackProvider {
    fun setPlaybackListener(listener: PlaybackListener)
    fun release() {}
}
