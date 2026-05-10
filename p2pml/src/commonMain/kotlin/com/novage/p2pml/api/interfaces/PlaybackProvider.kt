package com.novage.p2pml.api.interfaces

import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.api.models.PlaylistSnapshot
import kotlinx.coroutines.flow.StateFlow

interface PlaybackProvider {
    val playbackUpdates: StateFlow<PlaybackInfo>

    suspend fun getAbsolutePlaybackPosition(snapshot: PlaylistSnapshot): Double

    suspend fun clearState()
    
    fun release() {}
}
