package com.novage.p2pml.api.playback

import kotlin.concurrent.Volatile

/**
 * A platform-agnostic base class for custom [PlaybackProvider] implementations.
 *
 * Java or Swift consumers should extend this class when integrating custom video players
 * (e.g. VLC, WebOS players, or any non-ExoPlayer/AVPlayer setup).
 *
 * Report your player's **raw** values through [notifyPlaybackInfoUpdated]; the library maps them onto
 * the timeline it built from the manifest. There is deliberately nothing here to configure: no
 * absolute or wall-clock time is needed, and none should be supplied.
 *
 * **Threading:** call [notifyPlaybackInfoUpdated] from your player's native listeners or callbacks
 * (typically the UI/main thread).
 */
abstract class CustomPlaybackProvider : PlaybackProvider {
    @Volatile
    private var listener: PlaybackListener? = null

    final override fun setPlaybackListener(listener: PlaybackListener?) {
        this.listener = listener
    }

    /**
     * Pushes a playback progress update. Call periodically (e.g. once per second) from your player's
     * time observer, and additionally whenever the position jumps.
     *
     * @param positionSec The player's current position, in seconds, on whatever scale the player uses.
     * @param speed The current playback speed multiplier (e.g. 1.0).
     * @param liveEdgePositionSec For live streams, the live edge **on the same scale as
     *   [positionSec]** — for example the end of the player's seekable/DVR range. Pass `null` for
     *   on-demand content. Only the gap between the two values is used, so a player whose position
     *   scale slides as the live window advances needs no special handling.
     */
    fun notifyPlaybackInfoUpdated(positionSec: Double, speed: Float, liveEdgePositionSec: Double?) {
        listener?.onPlaybackInfoUpdated(PlaybackInfo(positionSec, speed, liveEdgePositionSec))
    }

    /** Convenience overload for on-demand content, and for Swift callers avoiding default arguments. */
    fun notifyPlaybackInfoUpdated(positionSec: Double, speed: Float) {
        notifyPlaybackInfoUpdated(positionSec, speed, null)
    }

    override fun release() {
        listener = null
    }
}
