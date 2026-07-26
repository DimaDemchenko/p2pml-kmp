package com.novage.p2pml.internal.playback

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.api.playback.PlaybackInfo
import com.novage.p2pml.api.playback.PlaybackListener
import com.novage.p2pml.api.playback.PlaybackProvider
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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

    // media3 requires Player access on the player's applicationLooper (main by default, but
    // reconfigurable via setLooper); Player.Listener callbacks already arrive on that looper.
    private val playerHandler = Handler(exoPlayer.applicationLooper)

    // Minimal Handler-backed dispatcher so this stays a coroutines-core-only module. delay()
    // uses the default timer and resumes back here, i.e. on the player's looper. A post to a
    // quit looper is dropped, which is fine: the player is gone and the scope gets cancelled.
    private val playerDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            playerHandler.post(block)
        }
    }
    private val providerScope = CoroutineScope(playerDispatcher + SupervisorJob())
    private var progressTrackerJob: Job? = null

    private val window = Timeline.Window()

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
                exoPlayer.removeListener(playerListener)
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
        val positionSec = exoPlayer.currentPosition / MILLISECONDS_IN_SECOND
        listener?.onPlaybackInfoUpdated(PlaybackInfo(positionSec, speed, resolveLiveEdgeSec()))
    }

    /**
     * The live edge on the same scale as [ExoPlayer.getCurrentPosition], or null for on-demand.
     *
     * `currentPosition` is measured from the start of the live window, and `durationMs` is that
     * window's length, so the window end is the live edge on that same scale. Both shift together as
     * the playlist slides, which is exactly why this is read fresh on every tick and never cached.
     */
    private fun resolveLiveEdgeSec(): Double? {
        val timeline = exoPlayer.currentTimeline
        if (timeline.isEmpty) return null

        timeline.getWindow(exoPlayer.currentMediaItemIndex, window)
        if (!window.isLive || window.durationMs == C.TIME_UNSET) return null

        return window.durationMs / MILLISECONDS_IN_SECOND
    }

    override fun release() {
        providerScope.cancel()
        listener = null
        playerHandler.post {
            exoPlayer.removeListener(playerListener)
        }
    }
}
