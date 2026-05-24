package com.novage.p2pml.internal.providers

import com.novage.p2pml.api.interfaces.PlaybackListener
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.internal.utils.getCurrentEpochSeconds
import kotlin.concurrent.Volatile
import kotlin.native.ref.WeakReference
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
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

    private var timeObserverToken: Any? = null
    private var syntheticWindowStartSec: Double? = null

    private var currentItemRef: AVPlayerItem? = null

    private val currentDateSelector = NSSelectorFromString("currentDate")

    init {
        val interval = CMTimeMakeWithSeconds(UPDATE_INTERVAL_SEC, 1)
        val weakThis = WeakReference(this)

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
            val absolutePositionSec = ref.resolveAbsolutePosition(ref.player.currentItem, relativePositionSec)

            ref.listener?.onPlaybackInfoUpdated(PlaybackInfo(absolutePositionSec, speed))
        }
    }

    override fun setPlaybackListener(listener: PlaybackListener?) {
        this.listener = listener
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
        val isLive = durationSec.isNaN() || durationSec.isInfinite() || durationSec <= 0.0

        if (isLive) {
            if (syntheticWindowStartSec == null) {
                syntheticWindowStartSec = getCurrentEpochSeconds() - relativePositionSec
            }
            return syntheticWindowStartSec!! + relativePositionSec
        }

        syntheticWindowStartSec = null
        return relativePositionSec
    }

    private fun getPlayerItemCurrentDate(item: AVPlayerItem): NSDate? {
        if (!item.respondsToSelector(currentDateSelector)) return null
        return item.performSelector(currentDateSelector)?.let { it as? NSDate }
    }

    override fun release() {
        timeObserverToken?.let { token ->
            dispatch_async(dispatch_get_main_queue()) {
                player.removeTimeObserver(token)
            }
            timeObserverToken = null
        }
        listener = null
    }
}
