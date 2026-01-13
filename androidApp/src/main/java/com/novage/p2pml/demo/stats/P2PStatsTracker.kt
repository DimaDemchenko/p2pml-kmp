package com.novage.p2pml.demo.stats

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.novage.p2pml.P2PMediaLoader
import com.novage.p2pml.domain.interfaces.Cancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class P2PStats(
    val bytesDownloadedHttp: Long = 0L,
    val bytesDownloadedP2p: Long = 0L,
    val bytesUploaded: Long = 0L,
    val connectedPeers: Set<String> = emptySet()
)

class P2PStatsTracker
@OptIn(UnstableApi::class)
constructor(private val p2pMediaLoader: P2PMediaLoader) {
    private val _statsFlow = MutableStateFlow(P2PStats())
    val statsFlow: StateFlow<P2PStats> = _statsFlow

    private val subscriptions = mutableListOf<Cancellable>()

    @OptIn(UnstableApi::class)
    fun startTracking() {
        subscriptions.add(
            p2pMediaLoader.onChunkDownloaded { data ->
                val currentStats = _statsFlow.value
                if (data.downloadSource == "http") {
                    _statsFlow.value = currentStats.copy(
                        bytesDownloadedHttp = currentStats.bytesDownloadedHttp + data.bytesLength
                    )
                } else if (data.downloadSource == "p2p") {
                    _statsFlow.value = currentStats.copy(
                        bytesDownloadedP2p = currentStats.bytesDownloadedP2p + data.bytesLength
                    )
                }
            }
        )

        subscriptions.add(
            p2pMediaLoader.onChunkUploaded { data ->
                val currentStats = _statsFlow.value
                _statsFlow.value = currentStats.copy(
                    bytesUploaded = currentStats.bytesUploaded + data.bytesLength
                )
            }
        )

        subscriptions.add(
            p2pMediaLoader.onPeerConnect { data ->
                val updatedPeers = _statsFlow.value.connectedPeers.toMutableSet().apply {
                    add(data.peerId)
                }
                _statsFlow.value = _statsFlow.value.copy(connectedPeers = updatedPeers)
            }
        )

        subscriptions.add(
            p2pMediaLoader.onPeerClose { data ->
                val updatedPeers = _statsFlow.value.connectedPeers.toMutableSet().apply {
                    remove(data.peerId)
                }
                _statsFlow.value = _statsFlow.value.copy(connectedPeers = updatedPeers)
            }
        )
    }

    @OptIn(UnstableApi::class)
    fun stopTracking() {
        subscriptions.forEach { it.cancel() }
        subscriptions.clear()
    }
}
