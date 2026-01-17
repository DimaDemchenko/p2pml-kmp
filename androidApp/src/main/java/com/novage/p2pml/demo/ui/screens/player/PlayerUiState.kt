package com.novage.p2pml.demo.ui.screens.player

import com.novage.p2pml.domain.models.PeerDetails

private const val PERCENT_MULTIPLIER = 100

data class PlayerUiState(
    val isInitializing: Boolean = true,
    // Stats
    val totalDownloaded: Long = 0,
    val p2pDownloaded: Long = 0,
    val httpDownloaded: Long = 0,
    val uploadTotal: Long = 0,
    // Peer Swarm
    val peers: List<PeerDetails> = emptyList(),
    // Status
    val isP2PActive: Boolean = false,
    val errorMessage: String? = null
) {
    val p2pPercentage: Int
        get() = if (totalDownloaded > 0) {
            ((p2pDownloaded.toFloat() / totalDownloaded) * PERCENT_MULTIPLIER).toInt()
        } else {
            0
        }
}
