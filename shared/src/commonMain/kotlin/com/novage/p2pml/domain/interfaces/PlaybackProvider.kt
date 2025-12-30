package com.novage.p2pml.domain.interfaces

import com.novage.p2pml.domain.models.PlaybackInfo
import com.novage.p2pml.domain.models.PlaylistSnapshot

interface PlaybackProvider {
    suspend fun getAbsolutePlaybackPosition(snapshot: PlaylistSnapshot): Double

    suspend fun getPlaybackPositionAndSpeed(): PlaybackInfo

    suspend fun resetData()
}