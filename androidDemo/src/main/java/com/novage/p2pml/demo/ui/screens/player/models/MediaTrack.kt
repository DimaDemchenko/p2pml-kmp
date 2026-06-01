package com.novage.p2pml.demo.ui.screens.player.models

import androidx.compose.runtime.Immutable

@Immutable
data class MediaTrack(
    val label: String,
    val isSelected: Boolean,
    val groupIndex: Int,
    val trackIndex: Int,
    val trackType: Int,
    val isAuto: Boolean = false
)

@Immutable
data class AvailableTracks(
    val videoTracks: List<MediaTrack> = emptyList(),
    val audioTracks: List<MediaTrack> = emptyList()
)
