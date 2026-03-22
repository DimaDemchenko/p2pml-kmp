package com.novage.p2pml.demo.ui.screens.player

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.navigation.toRoute
import com.novage.p2pml.P2PMediaLoader
import com.novage.p2pml.P2PMediaLoaderErrorType
import com.novage.p2pml.api.interfaces.Cancellable
import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.demo.ui.screens.player.models.MediaTrack
import com.novage.p2pml.demo.ui.screens.player.utils.applyTrackSelection
import com.novage.p2pml.demo.ui.screens.player.utils.getAvailableTracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.novage.p2pml.demo.ui.navigation.Player as PlayerRoute

private const val HIGH_DEMAND_WINDOW_SEC = 20
private const val PLAYER_MAX_BUFFER_MS = HIGH_DEMAND_WINDOW_SEC * 1000
private const val PLAYER_MIN_BUFFER_MS = 10_000
private const val BUFFER_FOR_PLAYBACK_MS = 2_500
private const val BUFFER_FOR_REBUFFER_MS = 5_000

class PlayerViewModel(application: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var currentTracks: Tracks? = null

    private var shouldAutoPlay = true
    var player: ExoPlayer? = null
        private set

    private var p2pLoader: P2PMediaLoader? = null
    private val eventSubscriptions = mutableListOf<Cancellable>()
    private var playerInitializationJob: Job? = null

    init {
        val args = savedStateHandle.toRoute<PlayerRoute>()
        initializePlayer(args.videoUrl, args.customEngineUrl)
    }

    @OptIn(UnstableApi::class)
    fun initializePlayer(manifestUrl: String, customEngineUrl: String?) {
        if (player != null || playerInitializationJob?.isActive == true) return

        playerInitializationJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val exoPlayer = withContext(Dispatchers.IO) {
                ExoPlayer.Builder(context)
                    .setLoadControl(
                        configureBufferSettings()
                    )
                    .setLooper(Looper.getMainLooper())
                    .build()
            }

            ensureActive()

            exoPlayer.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    currentTracks = tracks

                    val tracks = getAvailableTracks(tracks, exoPlayer.trackSelectionParameters)
                    _uiState.update { it.copy(availableTracks = tracks) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_READY) return

                    _uiState.update { it.copy(isVideoReady = true) }
                }
            })

            exoPlayer.playWhenReady = shouldAutoPlay
            Log.d("PlayerViewModel", "Starting ExoPlayer with P2P Media Loader shouldAutoPlay=$shouldAutoPlay")
            player = exoPlayer

            initializeP2PLoader(context, exoPlayer, manifestUrl, customEngineUrl)
        }
    }

    private fun initializeP2PLoader(
        context: Context,
        exoPlayer: ExoPlayer,
        manifestUrl: String,
        customEngineUrl: String?
    ) {
        P2PMediaLoader.enableLogging()

        val coreConfig = CoreConfig(
            highDemandTimeWindow = HIGH_DEMAND_WINDOW_SEC,
            isP2PDisabled = !shouldAutoPlay,
            simultaneousP2PDownloads = 3,
            webRtcMaxMessageSize = 65535,
            p2pNotReceivingBytesTimeoutMs = 1000,

            validateHTTPSegmentJs = """
            (url, byteRange, data) => {
                console.log(`Validating segment: ${'$'}{url} Range: ${'$'}{byteRange}`);
                return data.byteLength > 0;
            }
        """.trimIndent()
        )

        val loader = P2PMediaLoader(
            context = context,
            coreConfig = coreConfig,
            customEngineUrl = customEngineUrl,
            onReady = {
                val activeLoader = p2pLoader ?: return@P2PMediaLoader
                val p2pUrl = try {
                    activeLoader.getManifestUrl(manifestUrl)
                } catch (_: IllegalStateException) {
                    manifestUrl
                }

                startPlayback(exoPlayer, p2pUrl)
                _uiState.update { it.copy(isP2PActive = true) }
            },
            onError = { type, msg ->
                handleP2PError(type, msg, manifestUrl)
            }
        )

        setupP2PEvents(loader)
        loader.start(exoPlayer)
        p2pLoader = loader
    }

    fun onMessageConsumed() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun handleP2PError(type: P2PMediaLoaderErrorType, msg: String, originalUrl: String) {
        val exoPlayer = player ?: return

        when (type) {
            P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR,
            P2PMediaLoaderErrorType.ENGINE_RUNTIME_ERROR -> {
                startPlayback(exoPlayer, originalUrl)

                _uiState.update {
                    it.copy(
                        isP2PActive = false,
                        userMessage = "P2P Engine failed. Switched to HTTP mode."
                    )
                }
            }

            P2PMediaLoaderErrorType.MANIFEST_LOAD_ERROR,
            P2PMediaLoaderErrorType.MANIFEST_PARSE_ERROR -> {
                _uiState.update {
                    it.copy(
                        fatalError = "Video unavailable: $msg"
                    )
                }
            }

            P2PMediaLoaderErrorType.SEGMENT_DOWNLOAD_ERROR -> TODO()
        }
    }

    fun changeTrack(track: MediaTrack) {
        val player = player ?: return
        val tracks = currentTracks ?: return

        applyTrackSelection(player, track, tracks)

        val updatedTracks = getAvailableTracks(tracks, player.trackSelectionParameters)
        _uiState.update { it.copy(availableTracks = updatedTracks) }
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
        shouldAutoPlay = true
        player?.play()
        setP2PEnabled(true)
    }

    fun pause() {
        shouldAutoPlay = false
        player?.pause()
        setP2PEnabled(false)
    }

    private fun setP2PEnabled(isEnabled: Boolean) {
        val loader = p2pLoader ?: return

        val config = DynamicCoreConfig(
            isP2PDisabled = !isEnabled
        )

        loader.applyDynamicConfig(config)
    }

    private fun releaseResources() {
        playerInitializationJob?.cancel()

        eventSubscriptions.forEach { it.cancel() }
        eventSubscriptions.clear()

        player?.release()
        player = null

        p2pLoader?.release()
        p2pLoader = null
    }
}
