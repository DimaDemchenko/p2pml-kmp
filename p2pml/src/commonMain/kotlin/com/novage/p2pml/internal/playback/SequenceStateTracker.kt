package com.novage.p2pml.internal.playback

import com.novage.p2pml.api.errors.P2PMediaLoaderErrorCode
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.playback.PlaybackInfo
import com.novage.p2pml.api.playback.PlaybackListener
import com.novage.p2pml.api.playback.PlaybackProvider
import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.utils.CoreLogger
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

internal class SequenceStateTracker(
    private val playbackProvider: PlaybackProvider,
    private val p2pEngine: P2PEngine,
    private val hlsManifestManager: HlsManifestManager,
    private val onFatalError: (P2PMediaLoaderException) -> Unit
) : PlaybackListener {
    private val logger = CoreLogger("SequenceStateTracker")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()

    private val playbackInfoFlow = MutableStateFlow(PlaybackInfo(0.0, 1.0f))

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

        scope.launch {
            playbackInfoFlow.collect { info ->
                processPlaybackUpdate(info)
            }
        }
    }

    override fun onPlaybackInfoUpdated(info: PlaybackInfo) {
        playbackInfoFlow.value = info
    }

    private suspend fun processPlaybackUpdate(actualInfo: PlaybackInfo) {
        mutex.withLock {
            val forcedPos = forcedPlaybackPosition
            val effectiveInfo = when {
                forcedPos == null -> actualInfo

                abs(actualInfo.currentPlayPosition - forcedPos) <= catchUpThresholdSec -> {
                    logger.i { "Native player caught up to seek target ($forcedPos). Resuming standard tracking." }
                    resumeStandardTrackingLocked()
                    actualInfo
                }

                else -> actualInfo.copy(currentPlayPosition = forcedPos)
            }

            updateEnginePlaybackInfoSafely(effectiveInfo)
        }
    }

    suspend fun onSegmentRequested(runtimeId: String) {
        val (manifestUrl, segment) = hlsManifestManager.getSegmentWithManifestByUrl(runtimeId) ?: run {
            logger.w { "Segment requested but not tracked in manifest: $runtimeId" }
            return
        }

        mutex.withLock {
            val lastState = trackStates[manifestUrl]

            val isFirstRequest = lastState == null
            val isExactMatch = lastState != null &&
                segment.externalId == lastState.lastId &&
                segment.startTime == lastState.lastStartTime
            val isNextSegment = lastState != null && segment.externalId == lastState.lastId + 1

            val isSequential = isFirstRequest || isExactMatch || isNextSegment

            trackStates[manifestUrl] = TrackState(segment.externalId, segment.startTime)

            if (!isSequential) {
                val duration = (segment.endTime - segment.startTime).coerceAtLeast(DEFAULT_CATCH_UP_THRESHOLD_SEC)
                logger.w { "SEEK DETECTED on $manifestUrl. Forcing position to ${segment.startTime}." }
                suspendPollingLocked(segment.startTime, duration)

                val info = playbackInfoFlow.value
                updateEnginePlaybackInfoSafely(info.copy(currentPlayPosition = segment.startTime))
            }
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
            logger.e(e) { "Serialization error updating P2P engine (e.g. NaN/Infinity)" }
        } catch (e: IllegalStateException) {
            logger.e(e) { "Fatal state error updating P2P engine" }
            onFatalError(
                P2PMediaLoaderException(
                    code = P2PMediaLoaderErrorCode.ENGINE_CRASHED,
                    message = "Engine bridge broken during playback sync: ${e.message}",
                    cause = e
                )
            )
        } catch (e: IllegalArgumentException) {
            logger.e(e) { "Argument error updating P2P engine" }
        }
    }
}
