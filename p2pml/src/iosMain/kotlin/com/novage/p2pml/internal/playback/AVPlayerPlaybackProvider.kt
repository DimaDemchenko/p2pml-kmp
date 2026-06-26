package com.novage.p2pml.internal.playback

import com.novage.p2pml.api.playback.PlaybackInfo
import com.novage.p2pml.api.playback.PlaybackListener
import com.novage.p2pml.api.playback.PlaybackProvider
import com.novage.p2pml.internal.utils.getCurrentEpochSeconds
import kotlin.concurrent.Volatile
import kotlin.native.ref.WeakReference
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.duration
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSDate
import platform.Foundation.NSSelectorFromString
import platform.Foundation.timeIntervalSince1970
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val UPDATE_INTERVAL_SEC = 1.0

@OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
internal class AVPlayerPlaybackProvider(private val player: AVPlayer) : PlaybackProvider {

    @Volatile
    private var listener: PlaybackListener? = null
    private var isListening = false

    private var timeObserverToken: Any? = null
    private var syntheticWindowStartSec: Double? = null

    private var currentItemRef: AVPlayerItem? = null

    private val currentDateSelector = NSSelectorFromString("currentDate")

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
                        val relativePositionSec = CMTimeGetSeconds(time)
                        if (relativePositionSec.isNaN() || relativePositionSec.isInfinite()) {
                            return@addPeriodicTimeObserverForInterval
                        }
                        val speed = ref.player.rate
                        val absolutePositionSec = ref.resolveAbsolutePosition(
                            ref.player.currentItem,
                            relativePositionSec
                        )
                        ref.listener?.onPlaybackInfoUpdated(PlaybackInfo(absolutePositionSec, speed))
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

    private fun resolveAbsolutePosition(currentItem: AVPlayerItem?, relativePositionSec: Double): Double {
        if (currentItem == null) return relativePositionSec

        if (currentItemRef != currentItem) {
            currentItemRef = currentItem
            syntheticWindowStartSec = null
        }

        val currentDate = getPlayerItemCurrentDate(currentItem)
        if (currentDate != null) {
            syntheticWindowStartSec = null
            return currentDate.timeIntervalSince1970
        }

        val durationSec = CMTimeGetSeconds(currentItem.duration)
        val isDurationIndefinite = durationSec.isNaN() || durationSec.isInfinite() || durationSec <= 0.0

        val isReady = currentItem.status == AVPlayerItemStatusReadyToPlay
        val isLive = isDurationIndefinite && isReady

        if (isLive) {
            val startSec = syntheticWindowStartSec
                ?: (getCurrentEpochSeconds() - relativePositionSec).also { syntheticWindowStartSec = it }
            return startSec + relativePositionSec
        }

        syntheticWindowStartSec = null
        return relativePositionSec
    }

    private fun getPlayerItemCurrentDate(item: AVPlayerItem): NSDate? {
        if (!item.respondsToSelector(currentDateSelector)) return null
        return item.performSelector(currentDateSelector)?.let { it as? NSDate }
    }

    override fun release() {
        listener = null
        dispatch_async(dispatch_get_main_queue()) {
            stopObserving()
        }
    }
}
