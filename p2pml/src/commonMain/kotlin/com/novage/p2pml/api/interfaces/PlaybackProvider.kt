package com.novage.p2pml.api.interfaces

import com.novage.p2pml.api.models.PlaybackInfo

interface PlaybackProvider {
    fun getPlaybackInfo(): PlaybackInfo
    fun release() {}
}
