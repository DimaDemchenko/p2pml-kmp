package com.novage.p2pml.api.events

import kotlinx.serialization.Serializable

/**
 * Represents the details about a loaded segment.
 *
 * @property segmentUrl The URL of the segment.
 * @property bytesLength The length of the segment in bytes.
 * @property downloadSource The source from which the segment was downloaded.
 * @property peerId The ID of the peer from which the segment was downloaded.
 * @property streamType The type of stream.
 */
@Serializable
data class SegmentLoadDetails(
    val segmentUrl: String,
    val bytesLength: Int,
    val downloadSource: DownloadSource,
    val peerId: String? = null,
    val streamType: String
)

/**
 * Represents details about a segment event.
 *
 * @property segment The segment that the event is about.
 * @property downloadSource The origin of the segment download.
 * @property peerId The ID of the peer from which the segment was downloaded.
 */
@Serializable
data class SegmentStartDetails(val segment: Segment, val downloadSource: DownloadSource, val peerId: String? = null)

/**
 * Represents details about a segment error event.
 *
 * @property error The error that caused the download to fail.
 * @property segment The segment that the event is about.
 * @property downloadSource The origin of the segment download.
 * @property peerId The ID of the peer from which the segment was downloaded.
 * @property streamType The type of stream.
 */
@Serializable
data class SegmentErrorDetails(
    val error: JsError,
    val segment: Segment,
    val downloadSource: DownloadSource,
    val peerId: String? = null,
    val streamType: String
)

/**
 * Represents details about a segment abort event.
 *
 * @property segment The segment that the event is about.
 * @property downloadSource The origin of the segment download.
 * @property peerId The ID of the peer from which the segment was downloaded.
 * @property streamType The type of stream.
 */
@Serializable
data class SegmentAbortDetails(
    val segment: Segment,
    val downloadSource: DownloadSource,
    val peerId: String? = null,
    val streamType: String
)
