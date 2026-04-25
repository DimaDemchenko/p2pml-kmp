package com.novage.p2pml.internal.providers

import com.novage.p2pml.P2PMediaLoaderErrorType
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.utils.RuntimeErrorDispatcher
import com.novage.p2pml.internal.utils.suspendRunCatching
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class SequenceStateTracker(
    private val playbackProvider: PlaybackProvider,
    private val p2pEngine: P2PEngine,
    private val hlsManifestManager: HlsManifestManager,
    private val errorDispatcher: RuntimeErrorDispatcher
) {
    private val logger = CoreLogger("SequenceStateTracker")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()

    private var pollingJob: Job? = null
    private var suspensionJob: Job? = null

    private var forcedPlaybackPosition: Double? = null
    private var catchUpThresholdSec: Double = DEFAULT_CATCH_UP_THRESHOLD_SEC
    private val isSuspended get() = forcedPlaybackPosition != null

    private val trackStates = mutableMapOf<String, TrackState>()

    private data class TrackState(val lastId: Long, val lastStartTime: Double)

    companion object {
        private const val PLAYBACK_UPDATE_INTERVAL_MS = 1000L
        private const val SUSPENSION_TIMEOUT_MS = 8000L
        private const val DEFAULT_CATCH_UP_THRESHOLD_SEC = 5.0
    }

    fun start() {
        if (pollingJob?.isActive == true) return
        logger.d { "Starting playback sequence poller." }

        pollingJob = scope.launch {
            while (isActive) {
                pollPlaybackInfo()
                delay(PLAYBACK_UPDATE_INTERVAL_MS)
            }
        }
    }

    private suspend fun pollPlaybackInfo() {
        val actualInfo = suspendRunCatching { playbackProvider.getPlaybackPositionAndSpeed() }
            .onFailure { e -> logger.e(e) { "Failed to poll native playback info: ${e.message}" } }
            .getOrNull() ?: return

        val effectiveInfo = mutex.withLock {
            val forcedPos = forcedPlaybackPosition ?: return@withLock actualInfo
            val diff = abs(actualInfo.currentPlayPosition - forcedPos)

            if (diff <= catchUpThresholdSec) {
                logger.i { "Native player caught up to seek target ($forcedPos). Resuming standard tracking." }
                resumePollingLocked()
                actualInfo
            } else {
                actualInfo.copy(currentPlayPosition = forcedPos)
            }
        }

        updateEnginePlaybackInfoSafely(effectiveInfo)
    }

    suspend fun onSegmentRequested(runtimeId: String) {
        start()

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
            suspendRunCatching { playbackProvider.getPlaybackPositionAndSpeed() }
                .onSuccess { info ->
                    updateEnginePlaybackInfoSafely(info.copy(currentPlayPosition = segment.startTime))
                }
                .onFailure { e -> logger.e(e) { "Failed to fetch native info for forced seek update: ${e.message}" } }
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
                    logger.w { "Seek suspension timeout ($SUSPENSION_TIMEOUT_MS ms) elapsed. Resuming polling." }
                    resumePollingLocked()
                }
            }
        }
    }

    private fun resumePollingLocked() {
        forcedPlaybackPosition = null
        suspensionJob?.cancel()
    }

    suspend fun reset() = mutex.withLock {
        trackStates.clear()
        resumePollingLocked()
    }

    fun destroy() {
        logger.i { "Destroying SequenceStateTracker..." }
        scope.cancel()
    }

    private fun updateEnginePlaybackInfoSafely(info: PlaybackInfo) {
        try {
            p2pEngine.updatePlaybackInfo(Json.encodeToString(info))
        } catch (e: SerializationException) {
            logger.e { "Serialization error updating P2P engine: ${e.message}" }
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
