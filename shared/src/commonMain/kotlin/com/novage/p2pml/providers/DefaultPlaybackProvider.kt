package com.novage.p2pml.providers

import com.novage.p2pml.PlaybackInfo
import com.novage.p2pml.parser.hlsPlaylistParser.HlsMediaPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultPlaybackProvider(private val getPlaybackInfo: () -> PlaybackInfo) : PlaybackProvider {
    override suspend fun getAbsolutePlaybackPosition(
        parsedMediaPlaylist: HlsMediaPlaylist
    ): Double =
        withContext(Dispatchers.Main) {
            return@withContext getPlaybackInfo().currentPlayPosition
        }

    override suspend fun getPlaybackPositionAndSpeed(): PlaybackInfo =
        withContext(Dispatchers.Main) {
            return@withContext getPlaybackInfo()
        }

    override suspend fun resetData() {}
}
