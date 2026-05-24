package com.novage.p2pml.api.interop

import com.novage.p2pml.api.interfaces.PlaybackListener
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.internal.utils.getCurrentEpochSeconds
import kotlin.concurrent.Volatile

/**
 * A platform-agnostic base class for custom [PlaybackProvider] implementations.
 *
 * Java or Swift consumers should extend this class when integrating custom video players
 * (e.g., VLC, WebOS players, or any non-ExoPlayer/AVPlayer setup).
 *
 * This class automatically handles the Absolute Epoch Time synchronization
 * required by the P2P engine for live streams. Custom developers only need to
 * periodically push updates using the [notifyPlaybackInfoUpdated] helper method.
 *
 * **Threading:** Custom implementations should call [notifyPlaybackInfoUpdated]
 * from their player's native listeners or callbacks (typically running on the UI/Main thread).
 */
abstract class CustomPlaybackProvider : PlaybackProvider {
    @Volatile
    private var listener: PlaybackListener? = null

    @Volatile
    private var syntheticWindowStartSec: Double? = null

    @Volatile
    private var currentVideoId: String? = null

    final override fun setPlaybackListener(listener: PlaybackListener) {
        this.listener = listener
    }

    /**
     * Pushes a playback progress update to the P2P engine.
     * Call this method periodically (e.g. every second) from your player's time observer or callback.
     *
     * @param relativePositionSec The player's relative playhead position in seconds.
     * @param speed The current playback speed/rate (e.g., 1.0f).
     * @param isLive True if the current stream is a live broadcast, false for VOD.
     * @param absolutePositionSec Optional absolute position as Unix Epoch seconds.
     * @param videoId Optional unique ID for the current video. Reset the timeline when changed.
     */
    fun notifyPlaybackInfoUpdated(
        relativePositionSec: Double,
        speed: Float,
        isLive: Boolean,
        absolutePositionSec: Double? = null,
        videoId: String? = null
    ) {
        if (videoId != null && currentVideoId != videoId) {
            currentVideoId = videoId
            syntheticWindowStartSec = null
        }

        val resolvedAbsolutePos = resolveAbsolutePosition(relativePositionSec, isLive, absolutePositionSec)
        listener?.onPlaybackInfoUpdated(PlaybackInfo(resolvedAbsolutePos, speed))
    }

    /**
     * Swift-friendly overload to bypass Swift/Objective-C default parameter limitations.
     */
    fun notifyPlaybackInfoUpdated(relativePositionSec: Double, speed: Float, isLive: Boolean) {
        notifyPlaybackInfoUpdated(relativePositionSec, speed, isLive, null, null)
    }

    private fun resolveAbsolutePosition(
        relativePositionSec: Double,
        isLive: Boolean,
        absolutePositionSec: Double?
    ): Double {
        if (absolutePositionSec != null) {
            syntheticWindowStartSec = null
            return absolutePositionSec
        }

        if (isLive) {
            val start = syntheticWindowStartSec ?: (getCurrentEpochSeconds() - relativePositionSec).also {
                syntheticWindowStartSec = it
            }
            return start + relativePositionSec
        }

        syntheticWindowStartSec = null
        return relativePositionSec
    }

    override fun release() {
        listener = null
    }
}
