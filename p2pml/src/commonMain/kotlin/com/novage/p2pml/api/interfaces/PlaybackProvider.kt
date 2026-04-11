package com.novage.p2pml.api.interfaces

import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.api.models.PlaylistSnapshot

interface PlaybackProvider {
    suspend fun getAbsolutePlaybackPosition(snapshot: PlaylistSnapshot): Double

    suspend fun getPlaybackPositionAndSpeed(): PlaybackInfo
}
