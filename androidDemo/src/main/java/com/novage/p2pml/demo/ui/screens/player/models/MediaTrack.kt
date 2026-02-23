package com.novage.p2pml.demo.ui.screens.player.models

data class MediaTrack(
    val label: String,
    val isSelected: Boolean,
    val groupIndex: Int,
    val trackIndex: Int,
    val trackType: Int,
    val isAuto: Boolean = false
)

data class AvailableTracks(
    val videoTracks: List<MediaTrack> = emptyList(),
    val audioTracks: List<MediaTrack> = emptyList()
)
