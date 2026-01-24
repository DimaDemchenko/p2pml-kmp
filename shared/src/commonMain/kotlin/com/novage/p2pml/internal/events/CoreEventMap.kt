package com.novage.p2pml.internal.events

import com.novage.p2pml.api.models.ChunkDownloadedDetails
import com.novage.p2pml.api.models.ChunkUploadedDetails
import com.novage.p2pml.api.models.PeerDetails
import com.novage.p2pml.api.models.PeerErrorDetails
import com.novage.p2pml.api.models.SegmentAbortDetails
import com.novage.p2pml.api.models.SegmentErrorDetails
import com.novage.p2pml.api.models.SegmentLoadDetails
import com.novage.p2pml.api.models.SegmentStartDetails
import com.novage.p2pml.api.models.TrackerErrorDetails
import com.novage.p2pml.api.models.TrackerWarningDetails

/**
 * CoreEventMap is a sealed class that represents the different types of events that can be emitted
 * by the P2P core.
 *
 * See
 * [P2P Media Loader CoreEventMap](https://novage.github.io/p2p-media-loader/docs/v2.1.0/types/p2p-media-loader-core.CoreEventMap.html)
 */
internal sealed class CoreEventMap<T>(val eventName: String) {
    companion object {
        /** Fired when a segment is fully downloaded and available for use. */
        val OnSegmentLoaded = OnSegmentLoadedEvent

        /** Fired at the beginning of a segment download process. */
        val OnSegmentStart = OnSegmentStartEvent

        /** Fired when an error occurs during the download of a segment. */
        val OnSegmentError = OnSegmentErrorEvent

        /** Fired if the download of a segment is aborted before completion. */
        val OnSegmentAbort = OnSegmentAbortEvent

        /** Fired when a new peer-to-peer connection is established. */
        val OnPeerConnect = OnPeerConnectEvent

        /** Fired when an existing peer-to-peer connection is closed. */
        val OnPeerClose = OnPeerCloseEvent

        /** Fired when an error occurs during a peer-to-peer connection. */
        val OnPeerError = OnPeerErrorEvent

        /** Fired after a chunk of data from a segment has been successfully downloaded. */
        val OnChunkDownloaded = OnChunkDownloadedEvent

        /** Fired when a chunk of data has been successfully uploaded to a peer. */
        val OnChunkUploaded = OnChunkUploadedEvent

        /** Fired when an error occurs during the tracker request process. */
        val OnTrackerError = OnTrackerErrorEvent

        /** Fired when a warning occurs during the tracker request process. */
        val OnTrackerWarning = OnTrackerWarningEvent
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
