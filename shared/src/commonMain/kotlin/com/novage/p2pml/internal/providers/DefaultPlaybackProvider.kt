package com.novage.p2pml.internal.providers

import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.api.models.PlaylistSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class DefaultPlaybackProvider(private val getPlaybackInfo: () -> PlaybackInfo) : PlaybackProvider {
    override suspend fun getAbsolutePlaybackPosition(snapshot: PlaylistSnapshot): Double =
        withContext(Dispatchers.Main) {
            return@withContext getPlaybackInfo().currentPlayPosition
        }

    override suspend fun getPlaybackPositionAndSpeed(): PlaybackInfo = withContext(Dispatchers.Main) {
        return@withContext getPlaybackInfo()
    }

    override suspend fun resetData() {
        // Intentionally empty for the default implementation
    }
}
