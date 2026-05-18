package com.novage.p2pml.api.interop

import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.internal.utils.getCurrentEpochSeconds

/**
 * A platform-agnostic base class for custom [PlaybackProvider] implementations.
 *
 * Java or Swift consumers should extend this class when integrating custom video players
 * (e.g., VLC, WebOS players, or any non-ExoPlayer/AVPlayer setup).
 *
 * This class automatically handles the Absolute Epoch Time synchronization
 * required by the P2P engine for live streams. Custom developers only need to
 * implement five simple native getters — no timeline math required.
 *
 * **Threading:** The abstract getters are called from a background thread.
 * If your player API requires main-thread access, cache the values in your
 * implementation (e.g., via a periodic main-thread observer) and return the
 * cached values from the getters.
 */
abstract class CustomPlaybackProvider : PlaybackProvider {

    private var syntheticWindowStartSec: Double? = null
    private var currentVideoId: String? = null

    /** @return The player's standard relative playhead position in seconds (e.g., 15.5). */
    abstract fun getRelativePositionSec(): Double

    /** @return The current playback speed/rate (e.g., 1.0f). */
    abstract fun getPlaybackSpeed(): Float

    /** @return True if the current stream is a live broadcast, false for VOD. */
    abstract fun isLiveStream(): Boolean

    /**
     * @return The absolute playback position as Unix Epoch seconds,
     * if available from the player (e.g., via `EXT-X-PROGRAM-DATE-TIME`
     * or an equivalent player API like AVPlayer's `currentDate`).
     * Return null to use the synthetic live-window fallback.
     */
    abstract fun getAbsolutePositionSec(): Double?

    /**
     * @return A unique ID for the current video (e.g., URL or playlist ID).
     * When this value changes, the provider automatically resets the internal live timeline.
     */
    abstract fun getCurrentVideoId(): String?

    /**
     * Resolved internally by the P2P engine. Custom developers should not override this.
     */
    final override fun getPlaybackInfo(): PlaybackInfo {
        val relativePositionSec = getRelativePositionSec()
        val isLive = isLiveStream()
        val videoId = getCurrentVideoId()

        if (videoId == null || currentVideoId != videoId) {
            currentVideoId = videoId
            syntheticWindowStartSec = null
        }

        val absolutePositionSec = resolveAbsolutePosition(
            relativePositionSec,
            isLive,
            getAbsolutePositionSec()
        )

        return PlaybackInfo(absolutePositionSec, getPlaybackSpeed())
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
            if (syntheticWindowStartSec == null) {
                syntheticWindowStartSec = getCurrentEpochSeconds() - relativePositionSec
            }
            return syntheticWindowStartSec!! + relativePositionSec
        }

        syntheticWindowStartSec = null
        return relativePositionSec
    }
}
