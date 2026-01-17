package com.novage.p2pml.demo.ui.screens.player.models

data class VideoQuality(
    val label: String,
    val isSelected: Boolean,
    val groupIndex: Int,
    val trackIndex: Int,
    val isAuto: Boolean = false
)
