package com.novage.p2pml.domain.interfaces

import com.novage.p2pml.domain.models.PlaybackInfo
import com.novage.p2pml.parser.hlsPlaylistParser.HlsMediaPlaylist

interface PlaybackProvider {
    suspend fun getAbsolutePlaybackPosition(parsedMediaPlaylist: HlsMediaPlaylist): Double

    suspend fun getPlaybackPositionAndSpeed(): PlaybackInfo

    suspend fun resetData()
}