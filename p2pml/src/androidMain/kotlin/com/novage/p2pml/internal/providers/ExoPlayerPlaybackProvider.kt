package com.novage.p2pml.internal.providers

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.api.interfaces.PlaybackListener
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaybackInfo
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MILLISECONDS_IN_SECOND = 1000.0
private const val UPDATE_INTERVAL_MS = 1000L

internal class ExoPlayerPlaybackProvider(private val exoPlayer: ExoPlayer) : PlaybackProvider {
    @Volatile
    private var listener: PlaybackListener? = null

    private val providerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTrackerJob: Job? = null

    private val window = Timeline.Window()
    private var syntheticWindowStartTimeMs: Long = C.TIME_UNSET

    private var currentWindowUid: Any? = null

    private val playerListener = object : Player.Listener {
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

    override fun setPlaybackListener(listener: PlaybackListener?) {
        this.listener = listener

        if (listener != null) {
            providerScope.launch {
                exoPlayer.addListener(playerListener)
                if (exoPlayer.isPlaying) startTrackingProgress()
            }
        } else {
            providerScope.launch {
                stopTrackingProgress()
                exoPlayer.removeListener(playerListener)
            }
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
        listener?.onPlaybackInfoUpdated(PlaybackInfo(absolutePositionSec, speed))
    }

    private fun resolveAbsolutePositionMs(relativePositionMs: Long): Double {
        val timeline = exoPlayer.currentTimeline
        if (timeline.isEmpty) return relativePositionMs.toDouble()

        timeline.getWindow(exoPlayer.currentMediaItemIndex, window)

        if (currentWindowUid != window.uid) {
            currentWindowUid = window.uid
            syntheticWindowStartTimeMs = C.TIME_UNSET
        }

        if (window.windowStartTimeMs != C.TIME_UNSET) {
            syntheticWindowStartTimeMs = C.TIME_UNSET
            return (window.windowStartTimeMs + relativePositionMs).toDouble()
        }

        if (window.isLive && window.durationMs != C.TIME_UNSET) {
            if (syntheticWindowStartTimeMs == C.TIME_UNSET) {
                syntheticWindowStartTimeMs = System.currentTimeMillis() - window.durationMs
            }
            return (syntheticWindowStartTimeMs + relativePositionMs).toDouble()
        }

        syntheticWindowStartTimeMs = C.TIME_UNSET
        return relativePositionMs.toDouble()
    }

    override fun release() {
        providerScope.cancel()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            exoPlayer.removeListener(listenerImpl)
        }
        listener = null
    }
}
