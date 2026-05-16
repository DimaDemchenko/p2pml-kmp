package com.novage.p2pml.api.interfaces

import com.novage.p2pml.api.models.PlaybackInfo
import kotlinx.coroutines.flow.StateFlow

interface PlaybackProvider {
    val playbackUpdates: StateFlow<PlaybackInfo>
    fun release() {}
}
