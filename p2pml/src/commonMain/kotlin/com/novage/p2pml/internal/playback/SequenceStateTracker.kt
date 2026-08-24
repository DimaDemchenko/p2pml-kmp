package com.novage.p2pml.internal.playback

import com.novage.p2pml.api.playback.PlaybackInfo
import com.novage.p2pml.api.playback.PlaybackListener
import com.novage.p2pml.api.playback.PlaybackProvider
import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.utils.CoreLogger
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

internal class SequenceStateTracker(
    private val playbackProvider: PlaybackProvider,
    private val p2pEngine: P2PEngine,
    private val timelineSource: PlaybackTimelineSource,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : PlaybackListener {
    private val logger = CoreLogger("SequenceStateTracker")
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val mutex = Mutex()

    private val playbackInfoFlow = MutableStateFlow<PlaybackInfo?>(null)

    private var suspensionJob: Job? = null

    private var forcedPlaybackPosition: Double? = null
    private var catchUpThresholdSec: Double = DEFAULT_CATCH_UP_THRESHOLD_SEC
    private val isSuspended get() = forcedPlaybackPosition != null

    /**
     * Distance from the player's reported position to [forcedPlaybackPosition] on the previous tick,
     * used to tell a real seek from a re-fetch. Reset when a suspension starts.
     */
    private var lastCatchUpDelta: Double = Double.POSITIVE_INFINITY

    private val trackStates = mutableMapOf<String, TrackState>()

    private data class TrackState(val lastId: Long, val lastStartTime: Double)

    companion object {
        private const val SUSPENSION_TIMEOUT_MS = 8000L
        private const val DEFAULT_CATCH_UP_THRESHOLD_SEC = 5.0
    }

    init {
        playbackProvider.setPlaybackListener(this)

        scope.launch {
            playbackInfoFlow.filterNotNull().collect { info ->
                processPlaybackUpdate(info)
            }
        }
    }

    override fun onPlaybackInfoUpdated(info: PlaybackInfo) {
        playbackInfoFlow.value = info
    }

    private suspend fun processPlaybackUpdate(playerInfo: PlaybackInfo) {
        // Resolved before taking this tracker's lock: the source guards itself with its own mutex, and
        // onSegmentRequested also reads it before locking here, so acquiring the two in opposite
        // orders would risk a deadlock.
        val bounds = timelineSource.getMainTimelineBounds()
        if (bounds == null) {
            logger.d { "No main-stream segments tracked yet; skipping playback update." }
            return
        }

        // From here on everything is on the parser's timeline, including the forced seek position, so
        // the catch-up comparison below compares like with like.
        val streamPosition = mapToStreamTime(playerInfo, bounds)

        mutex.withLock {
            val forcedPos = forcedPlaybackPosition
            val effectivePosition = when {
                forcedPos == null -> streamPosition

                abs(streamPosition - forcedPos) <= catchUpThresholdSec -> {
                    logger.i { "Native player caught up to seek target ($forcedPos). Resuming standard tracking." }
                    resumeStandardTrackingLocked()
                    streamPosition
                }

                // A seek converges: the player moves towards the target, so the gap shrinks every
                // tick. A gap that is not shrinking means the request that looked like a seek was
                // not one — AVPlayer re-fetches from the start of its buffer window after returning
                // from the background, which sits behind the playhead. Holding the inferred
                // position would then feed the engine a playhead seconds behind the player for the
                // rest of the suspension, and it could never catch up because the player is moving
                // away from it. Trust the player instead.
                abs(streamPosition - forcedPos) >= lastCatchUpDelta -> {
                    logger.i {
                        "Seek target ($forcedPos) is not converging with the player " +
                            "($streamPosition); the request was a re-fetch, not a seek. " +
                            "Resuming standard tracking."
                    }
                    resumeStandardTrackingLocked()
                    streamPosition
                }

                else -> {
                    lastCatchUpDelta = abs(streamPosition - forcedPos)
                    forcedPos
                }
            }

            updateEnginePlaybackInfoSafely(effectivePosition, playerInfo.currentPlaybackSpeed)
        }
    }

    suspend fun onSegmentRequested(runtimeId: String) {
        val (manifestUrl, segment) = timelineSource.getSegmentWithManifestByUrl(runtimeId) ?: run {
            logger.w { "Segment requested but not tracked in manifest: $runtimeId" }
            return
        }

        mutex.withLock {
            val lastState = trackStates[manifestUrl]

            val isExactMatch = lastState != null &&
                segment.externalId == lastState.lastId &&
                segment.startTime == lastState.lastStartTime
            val isNextSegment = lastState != null && segment.externalId == lastState.lastId + 1

            // A first request (lastState == null) must sync like a seek: the engine's playhead
            // starts at zero, and on a PROGRAM-DATE-TIME timeline the segments sit at epoch
            // values — without the sync the engine aborts every initial load and live playback
            // never starts.
            val isSequential = isExactMatch || isNextSegment

            trackStates[manifestUrl] = TrackState(segment.externalId, segment.startTime)

            if (!isSequential) {
                val duration = (segment.endTime - segment.startTime).coerceAtLeast(DEFAULT_CATCH_UP_THRESHOLD_SEC)
                if (lastState == null) {
                    logger.i { "Stream start on $manifestUrl. Syncing engine position to ${segment.startTime}." }
                } else {
                    logger.w { "SEEK DETECTED on $manifestUrl. Forcing position to ${segment.startTime}." }
                }
                suspendPollingLocked(segment.startTime, duration)

                val speed = playbackInfoFlow.value?.currentPlaybackSpeed ?: 1.0f
                updateEnginePlaybackInfoSafely(segment.startTime, speed)
            }
        }
    }

    private fun suspendPollingLocked(position: Double, segmentDuration: Double) {
        forcedPlaybackPosition = position
        catchUpThresholdSec = segmentDuration
        lastCatchUpDelta = Double.POSITIVE_INFINITY

        suspensionJob?.cancel()
        suspensionJob = scope.launch {
            delay(SUSPENSION_TIMEOUT_MS)
            mutex.withLock {
                if (isSuspended) {
                    logger.w {
                        "Seek suspension timeout ($SUSPENSION_TIMEOUT_MS ms) elapsed. " +
                            "Resuming standard tracking."
                    }
                    resumeStandardTrackingLocked()
                }
            }
        }
    }

    private fun resumeStandardTrackingLocked() {
        forcedPlaybackPosition = null
        suspensionJob?.cancel()
    }

    suspend fun reset() = mutex.withLock {
        trackStates.clear()
        resumeStandardTrackingLocked()
    }

    fun destroy() {
        logger.i { "Destroying SequenceStateTracker..." }
        playbackProvider.setPlaybackListener(null)
        scope.cancel()
    }

    private fun updateEnginePlaybackInfoSafely(positionSec: Double, speed: Float) {
        try {
            p2pEngine.updatePlaybackInfo(positionSec, speed)
        } catch (e: SerializationException) {
            logger.e(e) { "Serialization error updating P2P engine (e.g. NaN/Infinity)" }
        }
    }
}
