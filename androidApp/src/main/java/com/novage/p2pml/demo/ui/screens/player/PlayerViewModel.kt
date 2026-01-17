package com.novage.p2pml.demo.ui.screens.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import com.novage.p2pml.P2PMediaLoader
import com.novage.p2pml.domain.interfaces.Cancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

private const val HIGH_DEMAND_WINDOW_SEC = 20
private const val PLAYER_MAX_BUFFER_MS = HIGH_DEMAND_WINDOW_SEC * 1000
private const val PLAYER_MIN_BUFFER_MS = 10_000
private const val BUFFER_FOR_PLAYBACK_MS = 2_500
private const val BUFFER_FOR_REBUFFER_MS = 5_000

class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    var player: ExoPlayer? = null
        private set

    private var p2pLoader: P2PMediaLoader? = null
    private val eventSubscriptions = mutableListOf<Cancellable>()

    @OptIn(UnstableApi::class)
    fun initializePlayer(context: Context, manifestUrl: String) {
        if (player != null) return

        val loadControl = configureBufferSettings()
        val exoPlayer = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()

        exoPlayer.playWhenReady = true
        player = exoPlayer

        P2PMediaLoader.enableLogging()

        val loader = P2PMediaLoader(
            context = context,
            coreConfigJson = "{\"highDemandTimeWindow\": $HIGH_DEMAND_WINDOW_SEC }",
            onReady = {
                val activeLoader = p2pLoader ?: return@P2PMediaLoader
                val p2pUrl = try {
                    activeLoader.getManifestUrl(manifestUrl)
                } catch (_: Exception) {
                    manifestUrl
                }

                startPlayback(exoPlayer, p2pUrl)
                _uiState.update { it.copy(isP2PActive = true) }
            },
            onError = { error ->
                if (p2pLoader == null) return@P2PMediaLoader

                startPlayback(exoPlayer, manifestUrl)
                _uiState.update {
                    it.copy(
                        errorMessage = "P2P Failed. Using HTTP.",
                        isP2PActive = false
                    )
                }
            }
        )

        setupP2PEvents(loader)
        loader.start(exoPlayer)

        p2pLoader = loader
    }

    @OptIn(UnstableApi::class)
    private fun configureBufferSettings(): LoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            PLAYER_MIN_BUFFER_MS,
            PLAYER_MAX_BUFFER_MS,
            BUFFER_FOR_PLAYBACK_MS,
            BUFFER_FOR_REBUFFER_MS
        ).build()

    private fun startPlayback(exoPlayer: ExoPlayer, url: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()

        _uiState.update { it.copy(isInitializing = false) }
    }

    private fun setupP2PEvents(loader: P2PMediaLoader) {
        eventSubscriptions.add(
            loader.onChunkDownloaded { chunk ->
                _uiState.update { state ->
                    state.copy(
                        totalDownloaded = state.totalDownloaded + chunk.bytesLength,
                        p2pDownloaded = if (chunk.downloadSource == "p2p") {
                            state.p2pDownloaded + chunk.bytesLength
                        } else {
                            state.p2pDownloaded
                        },
                        httpDownloaded = if (chunk.downloadSource == "http") {
                            state.httpDownloaded + chunk.bytesLength
                        } else {
                            state.httpDownloaded
                        }
                    )
                }
            }
        )

        eventSubscriptions.add(
            loader.onChunkUploaded { chunk ->
                _uiState.update { state ->
                    state.copy(uploadTotal = state.uploadTotal + chunk.bytesLength)
                }
            }
        )

        eventSubscriptions.add(
            loader.onPeerConnect { peer ->
                _uiState.update { state ->
                    state.copy(peers = state.peers + peer)
                }
            }
        )

        eventSubscriptions.add(
            loader.onPeerClose { peer ->
                _uiState.update { state ->
                    state.copy(peers = state.peers.filter { it.peerId != peer.peerId })
                }
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        releaseResources()
    }

    fun play() {
        player?.play()
        setP2PEnabled(true)
    }

    fun pause() {
        player?.pause()
        setP2PEnabled(false)
    }

    private fun setP2PEnabled(isEnabled: Boolean) {
        val loader = p2pLoader ?: return

        val config = JSONObject().apply {
            put("isP2PDisabled", !isEnabled)
        }

        loader.applyDynamicConfig(config.toString())
    }

    private fun releaseResources() {
        eventSubscriptions.forEach { it.cancel() }
        eventSubscriptions.clear()

        player?.release()
        player = null

        p2pLoader?.release()
        p2pLoader = null
    }
}
