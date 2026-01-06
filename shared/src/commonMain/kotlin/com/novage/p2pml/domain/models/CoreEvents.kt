package com.novage.p2pml.domain.models

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField

/**
 * CoreEventMap is a sealed class that represents the different types of events that can be emitted
 * by the P2P core.
 *
 * See
 * [P2P Media Loader CoreEventMap](https://novage.github.io/p2p-media-loader/docs/v2.1.0/types/p2p-media-loader-core.CoreEventMap.html)
 */
sealed class CoreEventMap<T>(val eventName: String) {
    /**
     * Companion object holding all event singletons so Java can do: CoreEventMaps.OnSegmentLoaded
     * from Java code.
     */
    companion object {
        /** Fired when a segment is fully downloaded and available for use. */
        @JvmField val OnSegmentLoaded = OnSegmentLoadedEvent

        /** Fired at the beginning of a segment download process. */
        @JvmField val OnSegmentStart = OnSegmentStartEvent

        /** Fired when an error occurs during the download of a segment. */
        @JvmField val OnSegmentError = OnSegmentErrorEvent

        /** Fired if the download of a segment is aborted before completion. */
        @JvmField val OnSegmentAbort = OnSegmentAbortEvent

        /** Fired when a new peer-to-peer connection is established. */
        @JvmField val OnPeerConnect = OnPeerConnectEvent

        /** Fired when an existing peer-to-peer connection is closed. */
        @JvmField val OnPeerClose = OnPeerCloseEvent

        /** Fired when an error occurs during a peer-to-peer connection. */
        @JvmField val OnPeerError = OnPeerErrorEvent

        /** Fired after a chunk of data from a segment has been successfully downloaded. */
        @JvmField val OnChunkDownloaded = OnChunkDownloadedEvent

        /** Fired when a chunk of data has been successfully uploaded to a peer. */
        @JvmField val OnChunkUploaded = OnChunkUploadedEvent

        /** Fired when an error occurs during the tracker request process. */
        @JvmField val OnTrackerError = OnTrackerErrorEvent

        /** Fired when a warning occurs during the tracker request process. */
        @JvmField val OnTrackerWarning = OnTrackerWarningEvent
    }

    data object OnSegmentLoadedEvent : CoreEventMap<SegmentLoadDetails>("onSegmentLoaded")

    data object OnSegmentStartEvent : CoreEventMap<SegmentStartDetails>("onSegmentStart")

    data object OnSegmentErrorEvent : CoreEventMap<SegmentErrorDetails>("onSegmentError")

    data object OnSegmentAbortEvent : CoreEventMap<SegmentAbortDetails>("onSegmentAbort")

    data object OnPeerConnectEvent : CoreEventMap<PeerDetails>("onPeerConnect")

    data object OnPeerCloseEvent : CoreEventMap<PeerDetails>("onPeerClose")

    data object OnPeerErrorEvent : CoreEventMap<PeerErrorDetails>("onPeerError")

    data object OnChunkDownloadedEvent : CoreEventMap<ChunkDownloadedDetails>("onChunkDownloaded")

    data object OnChunkUploadedEvent : CoreEventMap<ChunkUploadedDetails>("onChunkUploaded")

    data object OnTrackerErrorEvent : CoreEventMap<TrackerErrorDetails>("onTrackerError")

    data object OnTrackerWarningEvent : CoreEventMap<TrackerWarningDetails>("onTrackerWarning")
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
    val downloadSource: String,
    val peerId: String? = null,
    val streamType: String,
)

/**
 * Represents details about a segment event.
 *
 * @property segment The segment that the event is about.
 * @property downloadSource The origin of the segment download.
 * @property peerId The ID of the peer from which the segment was downloaded.
 */
@Serializable
data class SegmentStartDetails(val segment: Segment, val downloadSource: String, val peerId: String? = null)

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
    val downloadSource: String,
    val peerId: String?,
    val streamType: String,
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
    val downloadSource: String,
    val peerId: String? = null,
    val streamType: String,
)

/**
 * Represents the details of a chunk downloaded event.
 *
 * @property bytesLength The length of the chunk in bytes.
 * @property downloadSource The source from which the chunk was downloaded.
 * @property peerId The ID of the peer from which the chunk was downloaded (if downloaded from a
 *   peer).
 */
data class ChunkDownloadedDetails(val bytesLength: Int, val downloadSource: String, val peerId: String?)

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
