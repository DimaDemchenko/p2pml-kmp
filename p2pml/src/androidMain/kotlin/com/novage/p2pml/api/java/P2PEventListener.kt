package com.novage.p2pml.api.java

import com.novage.p2pml.api.events.ChunkDownloadedDetails
import com.novage.p2pml.api.events.ChunkUploadedDetails
import com.novage.p2pml.api.events.PeerDetails
import com.novage.p2pml.api.events.PeerErrorDetails
import com.novage.p2pml.api.events.SegmentAbortDetails
import com.novage.p2pml.api.events.SegmentErrorDetails
import com.novage.p2pml.api.events.SegmentLoadDetails
import com.novage.p2pml.api.events.SegmentStartDetails
import com.novage.p2pml.api.events.TrackerErrorDetails
import com.novage.p2pml.api.events.TrackerWarningDetails

@JvmDefaultWithCompatibility
interface P2PEventListener {
    fun onSegmentLoaded(details: SegmentLoadDetails) {}
    fun onSegmentStart(details: SegmentStartDetails) {}
    fun onSegmentError(details: SegmentErrorDetails) {}
    fun onSegmentAbort(details: SegmentAbortDetails) {}
    fun onPeerConnect(details: PeerDetails) {}
    fun onPeerClose(details: PeerDetails) {}
    fun onPeerError(details: PeerErrorDetails) {}
    fun onChunkDownloaded(details: ChunkDownloadedDetails) {}
    fun onChunkUploaded(details: ChunkUploadedDetails) {}
    fun onTrackerError(details: TrackerErrorDetails) {}
    fun onTrackerWarning(details: TrackerWarningDetails) {}
}
