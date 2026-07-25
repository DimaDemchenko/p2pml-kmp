package com.novage.p2pml.api.java

import com.novage.p2pml.api.events.ChunkDownloadedDetails
import com.novage.p2pml.api.events.ChunkUploadedDetails
import com.novage.p2pml.api.events.PeerConnectErrorDetails
import com.novage.p2pml.api.events.PeerDetails
import com.novage.p2pml.api.events.PeerErrorDetails
import com.novage.p2pml.api.events.PeerWarningDetails
import com.novage.p2pml.api.events.SegmentAbortDetails
import com.novage.p2pml.api.events.SegmentErrorDetails
import com.novage.p2pml.api.events.SegmentLoadDetails
import com.novage.p2pml.api.events.SegmentStartDetails
import com.novage.p2pml.api.events.TrackerErrorDetails
import com.novage.p2pml.api.events.TrackerWarningDetails

/**
 * Callback interface for [P2PMediaLoaderJava] engine events. Every method has a default no-op body —
 * override only the events you consume. Each corresponds to a [P2PEventType]; pass the types you
 * want to [P2PMediaLoaderJava.addListener] so the engine emits only those.
 *
 * All callbacks are invoked on a background thread (`Dispatchers.Default`); switch to the main
 * thread before touching UI. Event payloads are documented on their respective `*Details` types.
 */
@JvmDefaultWithCompatibility
interface P2PEventListener {
    /** A segment finished loading (from peers or HTTP). See [SegmentLoadDetails]. */
    fun onSegmentLoaded(details: SegmentLoadDetails) {}

    /** A segment download started. See [SegmentStartDetails]. */
    fun onSegmentStart(details: SegmentStartDetails) {}

    /** A segment download failed. See [SegmentErrorDetails]. */
    fun onSegmentError(details: SegmentErrorDetails) {}

    /** A segment download was aborted (e.g. ABR switch or seek). See [SegmentAbortDetails]. */
    fun onSegmentAbort(details: SegmentAbortDetails) {}

    /** A peer connection was established. See [PeerDetails]. */
    fun onPeerConnect(details: PeerDetails) {}

    /** A peer connection attempt failed. See [PeerConnectErrorDetails]. */
    fun onPeerConnectError(details: PeerConnectErrorDetails) {}

    /** A peer disconnected. See [PeerDetails]. */
    fun onPeerClose(details: PeerDetails) {}

    /** A peer-level error occurred. See [PeerErrorDetails]. */
    fun onPeerError(details: PeerErrorDetails) {}

    /** A non-fatal peer warning. See [PeerWarningDetails]. */
    fun onPeerWarning(details: PeerWarningDetails) {}

    /** A chunk was downloaded (high-frequency). See [ChunkDownloadedDetails]. */
    fun onChunkDownloaded(details: ChunkDownloadedDetails) {}

    /** A chunk was uploaded to a peer (high-frequency). See [ChunkUploadedDetails]. */
    fun onChunkUploaded(details: ChunkUploadedDetails) {}

    /** A tracker request errored. See [TrackerErrorDetails]. */
    fun onTrackerError(details: TrackerErrorDetails) {}

    /** A tracker warning. See [TrackerWarningDetails]. */
    fun onTrackerWarning(details: TrackerWarningDetails) {}
}
