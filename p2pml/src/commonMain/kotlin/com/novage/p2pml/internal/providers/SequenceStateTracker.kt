package com.novage.p2pml.internal.providers

import com.novage.p2pml.P2PMediaLoaderErrorType
import com.novage.p2pml.api.interfaces.PlaybackListener
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.utils.RuntimeErrorDispatcher
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

internal class SequenceStateTracker(
    private val playbackProvider: PlaybackProvider,
    private val p2pEngine: P2PEngine,
    private val hlsManifestManager: HlsManifestManager,
    private val errorDispatcher: RuntimeErrorDispatcher
) : PlaybackListener {
    private val logger = CoreLogger("SequenceStateTracker")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()

    @Volatile
    private var latestPlaybackInfo = PlaybackInfo(0.0, 1.0f)

    private var suspensionJob: Job? = null

    private var forcedPlaybackPosition: Double? = null
    private var catchUpThresholdSec: Double = DEFAULT_CATCH_UP_THRESHOLD_SEC
    private val isSuspended get() = forcedPlaybackPosition != null

    private val trackStates = mutableMapOf<String, TrackState>()

    private data class TrackState(val lastId: Long, val lastStartTime: Double)

    companion object {
        private const val SUSPENSION_TIMEOUT_MS = 8000L
        private const val DEFAULT_CATCH_UP_THRESHOLD_SEC = 5.0
    }

    init {
        playbackProvider.setPlaybackListener(this)
    }

    override fun onPlaybackInfoUpdated(info: PlaybackInfo) {
        latestPlaybackInfo = info
        scope.launch {
            processPlaybackUpdate(info)
        }
    }

    private suspend fun processPlaybackUpdate(actualInfo: PlaybackInfo) {
        val effectiveInfo = mutex.withLock {
            val forcedPos = forcedPlaybackPosition ?: return@withLock actualInfo
            val diff = abs(actualInfo.currentPlayPosition - forcedPos)

            if (diff <= catchUpThresholdSec) {
                logger.i { "Native player caught up to seek target ($forcedPos). Resuming standard tracking." }
                resumeStandardTrackingLocked()
                actualInfo
            } else {
                actualInfo.copy(currentPlayPosition = forcedPos)
            }
        }

        updateEnginePlaybackInfoSafely(effectiveInfo)
    }

    suspend fun onSegmentRequested(runtimeId: String) {
        val (manifestUrl, segment) = hlsManifestManager.getSegmentWithManifestByUrl(runtimeId) ?: run {
            logger.w { "Segment requested but not tracked in manifest: $runtimeId" }
            return
        }

        val needsForcedUpdate = mutex.withLock {
            val lastState = trackStates[manifestUrl]

            val isFirstRequest = lastState == null
            val isExactMatch = lastState != null &&
                segment.externalId == lastState.lastId &&
                segment.startTime == lastState.lastStartTime
            val isNextSegment = lastState != null && segment.externalId == lastState.lastId + 1

            val isSequential = isFirstRequest || isExactMatch || isNextSegment

            trackStates[manifestUrl] = TrackState(segment.externalId, segment.startTime)

            if (isSequential) {
                false
            } else {
                val duration = (segment.endTime - segment.startTime).coerceAtLeast(DEFAULT_CATCH_UP_THRESHOLD_SEC)
                logger.w { "SEEK DETECTED on $manifestUrl. Forcing position to ${segment.startTime}." }
                suspendPollingLocked(segment.startTime, duration)
                true
            }
        }

        if (needsForcedUpdate) {
            val info = latestPlaybackInfo
            updateEnginePlaybackInfoSafely(info.copy(currentPlayPosition = segment.startTime))
        }
    }

    private fun suspendPollingLocked(position: Double, segmentDuration: Double) {
        forcedPlaybackPosition = position
        catchUpThresholdSec = segmentDuration

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

    private fun updateEnginePlaybackInfoSafely(info: PlaybackInfo) {
        try {
            p2pEngine.updatePlaybackInfo(info)
        } catch (e: SerializationException) {
            logger.e { "Serialization error updating P2P engine (e.g. NaN/Infinity): ${e.message}" }
        } catch (e: IllegalStateException) {
            logger.e { "Fatal state error updating P2P engine: ${e.message}" }
            errorDispatcher.tryEmit(
                P2PMediaLoaderErrorType.ENGINE_RUNTIME_ERROR,
                "Engine bridge broken during playback sync: ${e.message}"
            )
        } catch (e: IllegalArgumentException) {
            logger.e { "Argument error updating P2P engine: ${e.message}" }
        }
    }
}
