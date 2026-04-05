package com.novage.p2pml.internal.providers

import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.Segment
import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.utils.CoreLogger
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
    private val hlsManifestManager: HlsManifestManager
) {
    private val logger = CoreLogger("SequenceStateTracker")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()

    private var pollingJob: Job? = null
    private var suspensionJob: Job? = null

    private var forcedPlaybackPosition: Double? = null
    private val isSuspended: Boolean get() = forcedPlaybackPosition != null

    private val trackStates = mutableMapOf<String, TrackState>()

    private data class TrackState(val lastRequestedSegmentId: Long, val lastRequestedSegmentStartTime: Double)

    companion object {
        private const val PLAYBACK_UPDATE_INTERVAL_MS = 1000L
        private const val SUSPENSION_TIMEOUT_MS = 3000L
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
        try {
            val actualInfo = playbackProvider.getPlaybackPositionAndSpeed()
            val effectiveInfo = mutex.withLock {
                forcedPlaybackPosition?.let { forcedPos ->
                    actualInfo.copy(currentPlayPosition = forcedPos)
                } ?: actualInfo
            }

            p2pEngine.updatePlaybackInfo(Json.encodeToString(effectiveInfo))
        } catch (e: SerializationException) {
            logger.e { "Error polling playback info: ${e.message}" }
        }
    }

    suspend fun onSegmentRequested(segmentUrl: String) {
        start()

        val segmentInfo = hlsManifestManager.getSegmentWithManifestByUrl(segmentUrl)
        if (segmentInfo == null) {
            logger.w { "Segment requested but not tracked in manifest: $segmentUrl" }
            return
        }

        val (manifestUrl, segment) = segmentInfo

        val needsForcedUpdate = mutex.withLock {
            val isSequential = isSequentialSegment(manifestUrl, segment)
            trackStates[manifestUrl] = TrackState(segment.externalId, segment.startTime)

            if (isSequential) {
                if (isSuspended) {
                    logger.i {
                        "Sequential segment requested (${segment.externalId} on $manifestUrl). Resuming polling."
                    }
                    resumePollingLocked()
                }

                false
            } else {
                logger.i {
                    "Non-sequential segment detected on $manifestUrl. Seek assumed. " +
                        "Suspending polling and forcing pos: ${segment.startTime}"
                }
                suspendPollingLocked(segment.startTime)

                true
            }
        }

        if (needsForcedUpdate) {
            sendForcedPositionUpdate(segment.startTime)
        }
    }

    private suspend fun sendForcedPositionUpdate(position: Double) {
        try {
            val info = playbackProvider.getPlaybackPositionAndSpeed()
            p2pEngine.updatePlaybackInfo(Json.encodeToString(info.copy(currentPlayPosition = position)))
        } catch (e: SerializationException) {
            logger.e { "Failed to send forced position update: ${e.message}" }
        }
    }

    private fun isSequentialSegment(manifestUrl: String, segment: Segment): Boolean {
        val lastState = trackStates[manifestUrl] ?: return true

        val isExactMatch = segment.externalId == lastState.lastRequestedSegmentId &&
            segment.startTime == lastState.lastRequestedSegmentStartTime
        val isNextSegment = segment.externalId == lastState.lastRequestedSegmentId + 1

        return isExactMatch || isNextSegment
    }

    private fun suspendPollingLocked(position: Double) {
        forcedPlaybackPosition = position
        suspensionJob?.cancel()
        suspensionJob = scope.launch {
            delay(SUSPENSION_TIMEOUT_MS)
            mutex.withLock {
                if (isSuspended) {
                    logger.w { "Seek suspension timeout reached without sequential segments. Resuming polling." }
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
}
