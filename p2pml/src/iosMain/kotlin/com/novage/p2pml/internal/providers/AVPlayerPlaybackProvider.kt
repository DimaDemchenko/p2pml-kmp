package com.novage.p2pml.internal.providers

import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaybackInfo
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val UPDATE_INTERVAL_SEC = 1.0

@OptIn(ExperimentalForeignApi::class)
internal class AVPlayerPlaybackProvider(private val player: AVPlayer) : PlaybackProvider {

    private val _playbackUpdates = MutableStateFlow(PlaybackInfo(0.0, 1.0f))
    override val playbackUpdates: StateFlow<PlaybackInfo> = _playbackUpdates

    private var timeObserverToken: Any? = null

    init {
        val interval = CMTimeMakeWithSeconds(UPDATE_INTERVAL_SEC, 1)

        timeObserverToken = player.addPeriodicTimeObserverForInterval(
            interval,
            dispatch_get_main_queue()
        ) { time ->
            val rawPosition = CMTimeGetSeconds(time)
            if (rawPosition.isNaN() || rawPosition.isInfinite()) return@addPeriodicTimeObserverForInterval
            val speed = player.rate
            _playbackUpdates.value = PlaybackInfo(rawPosition, speed)
        }
    }

    override fun release() {
        timeObserverToken?.let { token ->
            dispatch_async(dispatch_get_main_queue()) {
                player.removeTimeObserver(token)
            }
            timeObserverToken = null
        }
    }
}
