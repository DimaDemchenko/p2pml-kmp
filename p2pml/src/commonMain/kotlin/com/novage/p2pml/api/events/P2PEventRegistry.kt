package com.novage.p2pml.api.events

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
import com.novage.p2pml.internal.engine.P2PEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class P2PEventRegistry internal constructor(
    private val coreScope: CoroutineScope,
    private val engineManagerProvider: () -> P2PEngine?,
    private val isCoreActive: () -> Boolean
) {
    private val _onSegmentLoaded = MutableSharedFlow<SegmentLoadDetails>()
    val onSegmentLoaded: SharedFlow<SegmentLoadDetails> = _onSegmentLoaded.asSharedFlow()

    private val _onSegmentStart = MutableSharedFlow<SegmentStartDetails>()
    val onSegmentStart: SharedFlow<SegmentStartDetails> = _onSegmentStart.asSharedFlow()

    private val _onSegmentError = MutableSharedFlow<SegmentErrorDetails>()
    val onSegmentError: SharedFlow<SegmentErrorDetails> = _onSegmentError.asSharedFlow()

    private val _onSegmentAbort = MutableSharedFlow<SegmentAbortDetails>()
    val onSegmentAbort: SharedFlow<SegmentAbortDetails> = _onSegmentAbort.asSharedFlow()

    private val _onPeerConnect = MutableSharedFlow<PeerDetails>()
    val onPeerConnect: SharedFlow<PeerDetails> = _onPeerConnect.asSharedFlow()

    private val _onPeerClose = MutableSharedFlow<PeerDetails>()
    val onPeerClose: SharedFlow<PeerDetails> = _onPeerClose.asSharedFlow()

    private val _onPeerError = MutableSharedFlow<PeerErrorDetails>()
    val onPeerError: SharedFlow<PeerErrorDetails> = _onPeerError.asSharedFlow()

    private val _onChunkDownloaded = MutableSharedFlow<ChunkDownloadedDetails>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val onChunkDownloaded: SharedFlow<ChunkDownloadedDetails> = _onChunkDownloaded.asSharedFlow()

    private val _onChunkUploaded = MutableSharedFlow<ChunkUploadedDetails>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val onChunkUploaded: SharedFlow<ChunkUploadedDetails> = _onChunkUploaded.asSharedFlow()

    private val _onTrackerError = MutableSharedFlow<TrackerErrorDetails>()
    val onTrackerError: SharedFlow<TrackerErrorDetails> = _onTrackerError.asSharedFlow()

    private val _onTrackerWarning = MutableSharedFlow<TrackerWarningDetails>()
    val onTrackerWarning: SharedFlow<TrackerWarningDetails> = _onTrackerWarning.asSharedFlow()

    internal fun emitSegmentLoaded(details: SegmentLoadDetails) = _onSegmentLoaded.tryEmit(details)
    internal fun emitSegmentStart(details: SegmentStartDetails) = _onSegmentStart.tryEmit(details)
    internal fun emitSegmentError(details: SegmentErrorDetails) = _onSegmentError.tryEmit(details)
    internal fun emitSegmentAbort(details: SegmentAbortDetails) = _onSegmentAbort.tryEmit(details)
    internal fun emitPeerConnect(details: PeerDetails) = _onPeerConnect.tryEmit(details)
    internal fun emitPeerClose(details: PeerDetails) = _onPeerClose.tryEmit(details)
    internal fun emitPeerError(details: PeerErrorDetails) = _onPeerError.tryEmit(details)
    internal fun emitChunkDownloaded(details: ChunkDownloadedDetails) = _onChunkDownloaded.tryEmit(details)
    internal fun emitChunkUploaded(details: ChunkUploadedDetails) = _onChunkUploaded.tryEmit(details)
    internal fun emitTrackerError(details: TrackerErrorDetails) = _onTrackerError.tryEmit(details)
    internal fun emitTrackerWarning(details: TrackerWarningDetails) = _onTrackerWarning.tryEmit(details)

    internal val flowsWithNames = listOf(
        "onSegmentLoaded" to _onSegmentLoaded,
        "onSegmentStart" to _onSegmentStart,
        "onSegmentError" to _onSegmentError,
        "onSegmentAbort" to _onSegmentAbort,
        "onPeerConnect" to _onPeerConnect,
        "onPeerClose" to _onPeerClose,
        "onPeerError" to _onPeerError,
        "onChunkDownloaded" to _onChunkDownloaded,
        "onChunkUploaded" to _onChunkUploaded,
        "onTrackerError" to _onTrackerError,
        "onTrackerWarning" to _onTrackerWarning
    )

    init {
        flowsWithNames.forEach { (eventName, flow) ->
            flow.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .onEach { hasSubscribers ->
                    val engine = engineManagerProvider() ?: return@onEach
                    if (!isCoreActive()) return@onEach

                    if (hasSubscribers) {
                        engine.subscribeToP2PEvent(eventName)
                    } else {
                        engine.unsubscribeFromP2PEvent(eventName)
                    }
                }
                .launchIn(coreScope)
        }
    }

    internal fun syncEarlySubscriptions() {
        val engine = engineManagerProvider() ?: return
        if (!isCoreActive()) return

        flowsWithNames.forEach { (eventName, flow) ->
            if (flow.subscriptionCount.value > 0) {
                engine.subscribeToP2PEvent(eventName)
            }
        }
    }
}
