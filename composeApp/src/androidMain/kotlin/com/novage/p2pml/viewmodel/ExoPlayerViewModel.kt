package com.novage.p2pml.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.novage.p2pml.P2PMediaLoader
import com.novage.p2pml.Streams
import com.novage.p2pml.stats.P2PStats
import com.novage.p2pml.stats.P2PStatsTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@UnstableApi
class ExoPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context
        get() = getApplication()

    val player: ExoPlayer by lazy {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10_000,
                15_000,
                2_500,
                5_000
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
    }
    private var p2pml: P2PMediaLoader? = null
    private var p2pStatsTracker: P2PStatsTracker? = null

    private val _p2pStats = MutableStateFlow(P2PStats())
    val p2pStats: StateFlow<P2PStats> get() = _p2pStats

    private val _loadingState = MutableStateFlow(true)
    val loadingState: StateFlow<Boolean> get() = _loadingState

    fun setupP2PML() {
        p2pml = P2PMediaLoader(context) {
            initializePlayback()
            p2pStatsTracker?.startTracking()
        }

        p2pStatsTracker = P2PStatsTracker(p2pml!!)
        p2pml!!.start(player)
        println(">>>> P2P Media Loader started")
        viewModelScope.launch {
            p2pStatsTracker?.statsFlow?.collectLatest { stats ->
                _p2pStats.value = stats
            }
        }
    }

    private fun initializePlayback() {
        val manifest = p2pml?.getManifestUrl(Streams.HLS_4K_STREAM)
            ?: throw IllegalStateException("P2PML is not started")
        val loggingDataSourceFactory = LoggingDataSourceFactory(context)

        val mediaSource = HlsMediaSource.Factory(loggingDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(manifest))

        player.apply {
            playWhenReady = true
            setMediaSource(mediaSource)
            prepare()
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        viewModelScope.launch {
                            _loadingState.value = false
                        }
                    }
                }
            })
        }
    }

    private fun onReadyError(message: String) {
        // Handle error
        Log.e("ExoPlayerViewModel", message)
    }

    fun releasePlayer() {
        player.release()
        p2pStatsTracker?.stopTracking()
        p2pml?.release()
    }

    fun updateP2PConfig(isP2PDisabled: Boolean) {
        val configJson = "{\"isP2PDisabled\": $isP2PDisabled}"
        p2pml?.applyDynamicConfig(configJson)
    }
}


@UnstableApi
class LoggingDataSourceFactory(
    context: Context,
) : DataSource.Factory {
    private val httpDataSourceFactory =
        DefaultHttpDataSource
            .Factory()
            // Set your connection parameters here
            .setConnectTimeoutMs(30000)
            // Set your read timeout here
            .setReadTimeoutMs(30000)

    private val baseDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    override fun createDataSource(): DataSource =
        LoggingDataSource(baseDataSourceFactory.createDataSource())
}


@UnstableApi
class LoggingDataSource(
    private val wrappedDataSource: DataSource,
) : DataSource {
    override fun open(dataSpec: DataSpec): Long {
        Log.d("HLSSegmentLogger", "Requesting: ${dataSpec.uri}")
        return try {
            wrappedDataSource.open(dataSpec)
        } catch (e: Exception) {
            Log.e("HLSSegmentLogger", "Error opening data source: ${e.message}", e)
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        try {
            wrappedDataSource.read(buffer, offset, length)
        } catch (e: Exception) {
            Log.e("HLSSegmentLogger", "Error reading data source: ${e.message}", e)
            throw e
        }

    override fun addTransferListener(transferListener: TransferListener) {
        wrappedDataSource.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = wrappedDataSource.uri

    override fun close() {
        try {
            wrappedDataSource.close()
        } catch (e: Exception) {
            Log.e("HLSSegmentLogger", "Error closing data source: ${e.message}", e)
        }
    }
}