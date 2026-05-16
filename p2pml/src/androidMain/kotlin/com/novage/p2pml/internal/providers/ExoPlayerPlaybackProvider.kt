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
        val absolutePositionSec = resolveAbsolutePositionMs(relativePositionMs) / MILLISECONDS_IN_SECOND
        _playbackUpdates.value = PlaybackInfo(absolutePositionSec, speed)
    }

    private fun resolveAbsolutePositionMs(relativePositionMs: Long): Double {
        val timeline = exoPlayer.currentTimeline
        if (timeline.isEmpty) return relativePositionMs.toDouble()

        timeline.getWindow(exoPlayer.currentMediaItemIndex, window)

        if (window.windowStartTimeMs != C.TIME_UNSET) {
            syntheticWindowStartTimeMs = C.TIME_UNSET
            return (window.windowStartTimeMs + relativePositionMs).toDouble()
        }

        if (window.isLive && window.defaultPositionMs != C.TIME_UNSET) {
            if (syntheticWindowStartTimeMs == C.TIME_UNSET) {
                syntheticWindowStartTimeMs = System.currentTimeMillis() - window.defaultPositionMs
            }
            return (syntheticWindowStartTimeMs + relativePositionMs).toDouble()
        }

        syntheticWindowStartTimeMs = C.TIME_UNSET
        return relativePositionMs.toDouble()
    }

    override fun release() {
        providerScope.cancel()
        Handler(Looper.getMainLooper()).post {
            exoPlayer.removeListener(listener)
        }
    }
}
