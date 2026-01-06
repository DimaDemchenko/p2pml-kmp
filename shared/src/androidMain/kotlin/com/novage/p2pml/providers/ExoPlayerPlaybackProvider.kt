package com.novage.p2pml.providers

import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.domain.interfaces.PlaybackProvider
import com.novage.p2pml.domain.models.PlaybackInfo
import com.novage.p2pml.domain.models.PlaylistSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class PlaybackSegment(
    val startTime: Double,
    val endTime: Double,
    val absoluteStartTime: Double,
    val absoluteEndTime: Double,
    val externalId: Long,
)

private const val MILLISECONDS_IN_SECOND = 1000.0

internal class ExoPlayerPlaybackProvider(private val exoPlayer: ExoPlayer) : PlaybackProvider {
    private var currentSnapshot: PlaylistSnapshot? = null
    private var currentAbsoluteTime: Double? = null

    private var currentSegments = mutableMapOf<Long, PlaybackSegment>()
    private val mutex = Mutex()

    private val nowInSeconds: Double
        get() = System.currentTimeMillis() / MILLISECONDS_IN_SECOND

    private fun removeObsoleteSegments(removeUntilId: Long) {
        currentSegments.entries.removeIf { it.key < removeUntilId }
    }

    private fun addSegment(duration: Double, externalId: Long) {
        val prevSegment = currentSegments[externalId - 1]

        val relativeStartTime = prevSegment?.endTime ?: 0.0
        val relativeEndTime = relativeStartTime + duration

        val absoluteStartTime = prevSegment?.absoluteEndTime
            ?: currentAbsoluteTime
            ?: nowInSeconds

        val absoluteEndTime = absoluteStartTime + duration

        currentSegments[externalId] = PlaybackSegment(
            startTime = relativeStartTime,
            endTime = relativeEndTime,
            absoluteStartTime = absoluteStartTime,
            absoluteEndTime = absoluteEndTime,
            externalId = externalId,
        )
    }

    override suspend fun getAbsolutePlaybackPosition(snapshot: PlaylistSnapshot): Double = mutex.withLock {
        currentSnapshot = snapshot

        if (currentAbsoluteTime == null) {
            currentAbsoluteTime = nowInSeconds
        }

        val newMediaSequence = snapshot.mediaSequence

        snapshot.segmentDurations.forEachIndexed { index, duration ->
            val segmentIndex = newMediaSequence + index

            if (!currentSegments.contains(segmentIndex)) {
                addSegment(duration, segmentIndex)
            }
        }

        removeObsoleteSegments(newMediaSequence)

        return@withLock checkNotNull(currentAbsoluteTime) { "Absolute time was not initialized" }
    }

    override suspend fun getPlaybackPositionAndSpeed(): PlaybackInfo = mutex.withLock {
        val (position, speed) = withContext(Dispatchers.Main) {
            (exoPlayer.currentPosition / MILLISECONDS_IN_SECOND) to exoPlayer.playbackParameters.speed
        }

        val snapshot = currentSnapshot
        if (snapshot == null || snapshot.hasEndTag) {
            return PlaybackInfo(position, speed)
        }

        val currentPlayback = position.coerceAtLeast(0.0)
        val currentSegment = currentSegments.values.find {
            currentPlayback >= it.startTime && currentPlayback <= it.endTime
        }

        if (currentSegment == null) {
            return PlaybackInfo(0.0, speed)
        }

        val segmentPlayTime = currentPlayback - currentSegment.startTime
        val segmentAbsolutePlayTime = currentSegment.absoluteStartTime + segmentPlayTime

        return PlaybackInfo(segmentAbsolutePlayTime, speed)
    }

    override suspend fun resetData() = mutex.withLock {
        currentSegments.clear()
        currentSnapshot = null
        currentAbsoluteTime = null
    }
}
