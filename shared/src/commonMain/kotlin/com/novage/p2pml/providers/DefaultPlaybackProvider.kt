package com.novage.p2pml.providers

import com.novage.p2pml.domain.interfaces.PlaybackProvider
import com.novage.p2pml.domain.models.PlaybackInfo
import com.novage.p2pml.domain.models.PlaylistSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultPlaybackProvider(private val getPlaybackInfo: () -> PlaybackInfo) : PlaybackProvider {
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
