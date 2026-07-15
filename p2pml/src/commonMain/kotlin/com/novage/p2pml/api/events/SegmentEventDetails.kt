package com.novage.p2pml.api.events

import kotlinx.serialization.Serializable

/**
 * Represents the details about a loaded segment.
 *
 * @property segment The segment that the event is about.
 * @property bytesLength The length of the segment in bytes.
 * @property downloadSource The source from which the segment was downloaded.
 * @property peerId The ID of the peer from which the segment was downloaded.
 * @property infoHash The info hash of the swarm that the segment belongs to.
 * @property streamType The type of stream.
 */
@Serializable
data class SegmentLoadDetails(
    val segment: Segment,
    val bytesLength: Int,
    val downloadSource: DownloadSource,
    val peerId: String? = null,
    val infoHash: String,
    val streamType: String
)

/**
 * Represents details about a segment event.
 *
 * @property segment The segment that the event is about.
 * @property downloadSource The origin of the segment download.
 * @property peerId The ID of the peer from which the segment was downloaded.
 * @property infoHash The info hash of the swarm that the segment belongs to.
 * @property streamType The type of stream.
 */
@Serializable
data class SegmentStartDetails(
    val segment: Segment,
    val downloadSource: DownloadSource,
    val peerId: String? = null,
    val infoHash: String,
    val streamType: String
)

/**
 * Represents details about a segment error event.
 *
 * @property error The error that caused the download to fail.
 * @property segment The segment that the event is about.
 * @property downloadSource The origin of the segment download.
 * @property peerId The ID of the peer from which the segment was downloaded.
 * @property infoHash The info hash of the swarm that the segment belongs to.
 * @property streamType The type of stream.
 */
@Serializable
data class SegmentErrorDetails(
    val error: JsError,
    val segment: Segment,
    val downloadSource: DownloadSource,
    val peerId: String? = null,
    val infoHash: String,
    val streamType: String
)

/**
 * Represents details about a segment abort event.
 *
 * @property segment The segment that the event is about.
 * @property downloadSource The origin of the segment download, or `null` when the request
 *   was aborted before any download attempt started.
 * @property peerId The ID of the peer from which the segment was downloaded.
 * @property infoHash The info hash of the swarm that the segment belongs to.
 * @property streamType The type of stream.
 */
@Serializable
data class SegmentAbortDetails(
    val segment: Segment,
    val downloadSource: DownloadSource? = null,
    val peerId: String? = null,
    val infoHash: String,
    val streamType: String
)
