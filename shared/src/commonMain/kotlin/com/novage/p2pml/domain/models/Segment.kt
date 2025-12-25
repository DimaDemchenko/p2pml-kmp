package com.novage.p2pml.domain.models

import kotlinx.serialization.Serializable

/**
 * Describes a media segment with its unique identifiers, location, and timing information.
 *
 * @property runtimeId The unique identifier of the segment.
 * @property externalId The external identifier of the segment.
 * @property url The URL of the segment.
 * @property byteRange The byte range of the segment.
 * @property startTime The start time of the segment.
 * @property endTime The end time of the segment.
 */
@Serializable
data class Segment(
    val runtimeId: String,
    val externalId: Long,
    val url: String,
    val byteRange: ByteRange?,
    val startTime: Double,
    val endTime: Double,
)