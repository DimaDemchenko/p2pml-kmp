package com.novage.p2pml.internal.providers

import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.api.models.PlaylistSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters

private data class PlaybackSegment(
    var startTime: Double,
    var endTime: Double,
    val absoluteStartTime: Double,
    val absoluteEndTime: Double,
    val externalId: Long
)

private const val MILLISECONDS_IN_SECOND = 1000.0

internal class ExoPlayerPlaybackProvider(private val exoPlayer: ExoPlayer) : PlaybackProvider {
    private var currentSnapshot: PlaylistSnapshot? = null
    private var currentSegments = mutableMapOf<Long, PlaybackSegment>()
    private val mutex = Mutex()

    private val nowInSeconds: Double
        get() = System.currentTimeMillis() / MILLISECONDS_IN_SECOND

    private val _playbackUpdates = MutableStateFlow(PlaybackInfo(0.0, 1.0f))
    override val playbackUpdates: StateFlow<PlaybackInfo> = _playbackUpdates

    private val providerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTrackerJob: Job? = null

    init {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startTrackingProgress() else stopTrackingProgress()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                emitCurrentState()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                emitCurrentState()
            }
        }

        providerScope.launch {
            exoPlayer.addListener(listener)
            if (exoPlayer.isPlaying) startTrackingProgress()
        }
    }

    private fun startTrackingProgress() {
        progressTrackerJob?.cancel()
        progressTrackerJob = providerScope.launch {
            while (isActive) {
                emitCurrentState()
                delay(1000)
            }
        }
    }

    private fun stopTrackingProgress() {
        progressTrackerJob?.cancel()
        progressTrackerJob = null
    }

    private fun emitCurrentState() {
        val rawPosition = exoPlayer.currentPosition / MILLISECONDS_IN_SECOND
        val speed = exoPlayer.playbackParameters.speed

        providerScope.launch(Dispatchers.Default) {
            val absolutePosition = mutex.withLock {
                val snapshot = currentSnapshot
                if (snapshot == null || snapshot.hasEndTag) {
                    return@withLock rawPosition
                }

                val currentPlayback = rawPosition.coerceAtLeast(0.0)
                val currentSegment = currentSegments.values.find {
                    currentPlayback >= it.startTime && currentPlayback <= it.endTime
                }

                if (currentSegment == null) {
                    return@withLock 0.0
                }

                val segmentPlayTime = currentPlayback - currentSegment.startTime
                currentSegment.absoluteStartTime + segmentPlayTime
            }
            _playbackUpdates.value = PlaybackInfo(absolutePosition, speed)
        }
    }

    private fun removeObsoleteSegments(removeUntilId: Long) {
        currentSegments.entries.removeIf { it.key < removeUntilId }
    }

    private fun updateExistingSegmentRelativeTime(segmentId: Long, durationSec: Double) {
        val prevSegment = currentSegments[segmentId - 1]
        val currentSegment =
            currentSegments[segmentId] ?: return

        val relativeStartTime = prevSegment?.endTime ?: 0.0
        val relativeEndTime = relativeStartTime + durationSec

        currentSegment.startTime = relativeStartTime
        currentSegment.endTime = relativeEndTime
    }

    private fun addSegment(durationSec: Double, externalId: Long) {
        val prevSegment = currentSegments[externalId - 1]

        val relativeStartTime = prevSegment?.endTime ?: 0.0
        val relativeEndTime = relativeStartTime + durationSec

        val absoluteStartTime = prevSegment?.absoluteEndTime ?: nowInSeconds
        val absoluteEndTime = absoluteStartTime + durationSec

        currentSegments[externalId] = PlaybackSegment(
            startTime = relativeStartTime,
            endTime = relativeEndTime,
            absoluteStartTime = absoluteStartTime,
            absoluteEndTime = absoluteEndTime,
            externalId = externalId
        )
    }

    override suspend fun getAbsolutePlaybackPosition(snapshot: PlaylistSnapshot): Double = mutex.withLock {
        currentSnapshot = snapshot

        val newMediaSequence = snapshot.mediaSequence

        removeObsoleteSegments(newMediaSequence)

        snapshot.segmentDurationsSec.forEachIndexed { index, duration ->
            val segmentIndex = newMediaSequence + index

            if (currentSegments.contains(segmentIndex)) {
                updateExistingSegmentRelativeTime(segmentIndex, duration)
            } else {
                addSegment(duration, segmentIndex)
            }
        }

        return@withLock nowInSeconds
    }

    override suspend fun clearState() = mutex.withLock {
        currentSegments.clear()
        currentSnapshot = null
    }

    override fun release() {
        providerScope.cancel()
    }
}
