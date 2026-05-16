package com.novage.p2pml.internal.providers

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaybackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MILLISECONDS_IN_SECOND = 1000.0
private const val UPDATE_INTERVAL_MS = 1000L

internal class ExoPlayerPlaybackProvider(private val exoPlayer: ExoPlayer) : PlaybackProvider {
    private val _playbackUpdates = MutableStateFlow(PlaybackInfo(0.0, 1.0f))
    override val playbackUpdates: StateFlow<PlaybackInfo> = _playbackUpdates

    private val providerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTrackerJob: Job? = null

    private val window = Timeline.Window()
    private var syntheticWindowStartTimeMs: Long = C.TIME_UNSET

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startTrackingProgress() else stopTrackingProgress()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            emitCurrentState()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            emitCurrentState()
        }
    }

    init {
        providerScope.launch {
            exoPlayer.addListener(listener)
            if (exoPlayer.isPlaying) startTrackingProgress()
        }
    }

    private fun startTrackingProgress() {
        progressTrackerJob?.cancel()
        progressTrackerJob = providerScope.launch {
            while (isActive) {
                emitCurrentState()
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopTrackingProgress() {
        progressTrackerJob?.cancel()
        progressTrackerJob = null
    }

    private fun emitCurrentState() {
        val speed = exoPlayer.playbackParameters.speed
        val relativePositionMs = exoPlayer.currentPosition
        val timeline = exoPlayer.currentTimeline

        var absolutePositionSec = relativePositionMs / MILLISECONDS_IN_SECOND

        if (!timeline.isEmpty) {
            timeline.getWindow(exoPlayer.currentMediaItemIndex, window)

            if (window.windowStartTimeMs != C.TIME_UNSET) {
                absolutePositionSec =
                    (window.windowStartTimeMs + relativePositionMs) / MILLISECONDS_IN_SECOND
                syntheticWindowStartTimeMs = C.TIME_UNSET
            } else if (window.isLive) {
                if (syntheticWindowStartTimeMs == C.TIME_UNSET) {
                    syntheticWindowStartTimeMs = System.currentTimeMillis() - relativePositionMs
                }
                absolutePositionSec =
                    (syntheticWindowStartTimeMs + relativePositionMs) / MILLISECONDS_IN_SECOND
            } else {
                syntheticWindowStartTimeMs = C.TIME_UNSET
            }
        }

        _playbackUpdates.value = PlaybackInfo(absolutePositionSec, speed)
    }

    override fun release() {
        providerScope.cancel()
        Handler(Looper.getMainLooper()).post {
            exoPlayer.removeListener(listener)
        }
    }
}
