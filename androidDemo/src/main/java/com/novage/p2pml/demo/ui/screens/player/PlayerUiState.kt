package com.novage.p2pml.demo.ui.screens.player

import com.novage.p2pml.api.models.PeerDetails
import com.novage.p2pml.demo.ui.screens.player.models.VideoQuality

data class PlayerUiState(
    val isVideoReady: Boolean = false,
    val isP2PActive: Boolean = false,
    val errorMessage: String? = null,

    val totalDownloaded: Long = 0,
    val p2pDownloaded: Long = 0,
    val httpDownloaded: Long = 0,
    val uploadTotal: Long = 0,
    val peers: List<PeerDetails> = emptyList(),

    val qualities: List<VideoQuality> = emptyList()
)
