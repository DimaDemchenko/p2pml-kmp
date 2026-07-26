package com.novage.p2pml.internal.playback

import com.novage.p2pml.api.playback.PlaybackInfo
import com.novage.p2pml.api.playback.PlaybackListener
import com.novage.p2pml.api.playback.PlaybackProvider
import kotlin.concurrent.Volatile
import kotlin.native.ref.WeakReference
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.CMTimeRangeValue
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.duration
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.seekableTimeRanges
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeRangeGetEnd
import platform.Foundation.NSValue
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val UPDATE_INTERVAL_SEC = 1.0

@OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
internal class AVPlayerPlaybackProvider(private val player: AVPlayer) : PlaybackProvider {

    @Volatile
    private var listener: PlaybackListener? = null
    private var isListening = false

    private var timeObserverToken: Any? = null

    override fun setPlaybackListener(listener: PlaybackListener?) {
        this.listener = listener
        dispatch_async(dispatch_get_main_queue()) {
            if (listener != null) {
                if (!isListening) {
                    isListening = true
                    val interval = CMTimeMakeWithSeconds(UPDATE_INTERVAL_SEC, 1)
                    val weakThis = WeakReference(this@AVPlayerPlaybackProvider)
                    timeObserverToken = player.addPeriodicTimeObserverForInterval(
                        interval,
                        dispatch_get_main_queue()
                    ) { time ->
                        val ref = weakThis.get() ?: return@addPeriodicTimeObserverForInterval
                        val positionSec = CMTimeGetSeconds(time)
                        if (positionSec.isNaN() || positionSec.isInfinite()) {
                            return@addPeriodicTimeObserverForInterval
                        }
                        val speed = ref.player.rate
                        val liveEdgeSec = ref.player.currentItem?.let { ref.resolveLiveEdgeSec(it) }
                        ref.listener?.onPlaybackInfoUpdated(PlaybackInfo(positionSec, speed, liveEdgeSec))
                    }
                }
            } else {
                stopObserving()
            }
        }
    }

    private fun stopObserving() {
        if (!isListening) return

        isListening = false
        timeObserverToken?.let { token -> player.removeTimeObserver(token) }
        timeObserverToken = null
    }

    /**
     * The live edge on the same scale as the item's current time, or null for on-demand content.
     *
     * An indefinite duration marks a live stream; its seekable range then spans the DVR window, whose
     * end is the live edge. Read fresh every tick, since that range slides.
     */
    private fun resolveLiveEdgeSec(item: AVPlayerItem): Double? {
        val durationSec = CMTimeGetSeconds(item.duration)
        val isLive = durationSec.isNaN() || durationSec.isInfinite() || durationSec <= 0.0
        if (!isLive) return null

        val lastRange = item.seekableTimeRanges.lastOrNull() as? NSValue ?: return null
        val endSec = CMTimeGetSeconds(CMTimeRangeGetEnd(lastRange.CMTimeRangeValue))

        return if (endSec.isNaN() || endSec.isInfinite()) null else endSec
    }

    override fun release() {
        listener = null
        dispatch_async(dispatch_get_main_queue()) {
            stopObserving()
        }
    }
}
