package com.novage.p2pml.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the source from which a segment or chunk was downloaded.
 */
@Serializable
enum class DownloadSource(val value: String) {
    /** The data was loaded via a peer-to-peer connection. */
    @SerialName("p2p") P2P("p2p"),
    /** The data was loaded via HTTP. */
    @SerialName("http") HTTP("http");

    companion object {
        fun fromValue(value: String): DownloadSource =
            entries.first { it.value == value }
    }
}

/**
 * Represents the details of a peer in a peer-to-peer network.
 *
 * @property peerId The unique identifier for a peer in the network.
 * @property streamType The type of stream that the peer is connected to.
 */
@Serializable data class PeerDetails(val peerId: String, val streamType: String)

/**
 * Represents the details of a peer error event.
 *
 * @property peerId The unique identifier for a peer in the network.
 * @property streamType The type of stream that the peer is connected to.
 * @property error The error that occurred during the peer-to-peer connection.
 */
@Serializable
data class PeerErrorDetails(val peerId: String, val streamType: String, val error: String)

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
 * @property error The error message.
 * @property segment The segment that the event is about.
 * @property downloadSource The origin of the segment download.
 * @property peerId The ID of the peer from which the segment was downloaded.
 * @property streamType The type of stream.
 */
@Serializable
data class SegmentErrorDetails(
    val error: String,
    val segment: Segment,
    val downloadSource: DownloadSource,
    val peerId: String?,
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

/**
 * Represents the details of a chunk downloaded event.
 *
 * @property bytesLength The length of the chunk in bytes.
 * @property downloadSource The source from which the chunk was downloaded.
 * @property peerId The ID of the peer from which the chunk was downloaded (if downloaded from a
 *   peer).
 */
data class ChunkDownloadedDetails(val bytesLength: Int, val downloadSource: DownloadSource, val peerId: String?)

/**
 * Represents the details of a chunk uploaded event.
 *
 * @property bytesLength The length of the chunk in bytes.
 * @property peerId The ID of the peer to which the chunk was uploaded.
 */
data class ChunkUploadedDetails(val bytesLength: Int, val peerId: String)

/**
 * Represents the details of a tracker error event.
 *
 * @property streamType The type of stream that the tracker is for.
 * @property error The error that occurred during the tracker request.
 */
@Serializable data class TrackerErrorDetails(val streamType: String, val error: JsError)

/**
 * Represents the details of a tracker warning event.
 *
 * @property streamType The type of stream that the tracker is for.
 * @property warning The warning that occurred during the tracker request.
 */
@Serializable data class TrackerWarningDetails(val streamType: String, val warning: JsError)

/**
 * Represents a JavaScript error.
 *
 * @property message The error message.
 * @property stack The stack trace of the error.
 */
@Serializable data class JsError(val message: String, val stack: String? = null)
