package com.novage.p2pml

import com.novage.p2pml.parser.hlsPlaylistParser.ByteRange
import kotlinx.serialization.Serializable

@Serializable internal data class Stream(val runtimeId: String, val type: String, val index: Int)

@Serializable
internal data class RuntimeSegment(
    val runtimeId: String,
    val externalId: Long,
    val url: String,
    val byteRange: ByteRange?,
    val startTime: Double,
    val endTime: Double,
)

@Serializable
internal data class UpdateStreamParams(
    val streamRuntimeId: String,
    val addSegments: List<RuntimeSegment>,
    val removeSegmentsIds: List<String>,
    val isLive: Boolean,
)

/**
 * Playback info
 *
 * @param currentPlayPosition current play position in seconds
 * @param currentPlaybackSpeed current play speed
 */
@Serializable
data class PlaybackInfo(val currentPlayPosition: Double, val currentPlaybackSpeed: Float)
