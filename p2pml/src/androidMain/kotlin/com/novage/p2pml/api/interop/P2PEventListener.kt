package com.novage.p2pml.api.interop

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
