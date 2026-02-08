package com.novage.p2pml.internal.providers

import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.api.models.PlaylistSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    override suspend fun getPlaybackPositionAndSpeed(): PlaybackInfo {
        val (position, speed) = withContext(Dispatchers.Main) {
            (exoPlayer.currentPosition / MILLISECONDS_IN_SECOND) to exoPlayer.playbackParameters.speed
        }

        return mutex.withLock {
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

            PlaybackInfo(segmentAbsolutePlayTime, speed)
        }
    }

    override suspend fun resetData() = mutex.withLock {
        currentSegments.clear()
        currentSnapshot = null
    }
}
