package com.novage.p2pml.providers

import com.novage.p2pml.PlaybackInfo
import com.novage.p2pml.parser.hlsPlaylistParser.HlsMediaPlaylist

internal interface PlaybackProvider {
    suspend fun getAbsolutePlaybackPosition(parsedMediaPlaylist: HlsMediaPlaylist): Double

    suspend fun getPlaybackPositionAndSpeed(): PlaybackInfo

    suspend fun resetData()
}
