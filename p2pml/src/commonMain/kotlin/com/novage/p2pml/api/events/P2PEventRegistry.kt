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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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

    private val _onChunkDownloaded = MutableSharedFlow<ChunkDownloadedDetails>()
    val onChunkDownloaded: SharedFlow<ChunkDownloadedDetails> = _onChunkDownloaded.asSharedFlow()

    private val _onChunkUploaded = MutableSharedFlow<ChunkUploadedDetails>()
    val onChunkUploaded: SharedFlow<ChunkUploadedDetails> = _onChunkUploaded.asSharedFlow()

    private val _onTrackerError = MutableSharedFlow<TrackerErrorDetails>()
    val onTrackerError: SharedFlow<TrackerErrorDetails> = _onTrackerError.asSharedFlow()

    private val _onTrackerWarning = MutableSharedFlow<TrackerWarningDetails>()
    val onTrackerWarning: SharedFlow<TrackerWarningDetails> = _onTrackerWarning.asSharedFlow()

    internal fun emitSegmentLoaded(d: SegmentLoadDetails) = coreScope.launch { _onSegmentLoaded.emit(d) }
    internal fun emitSegmentStart(d: SegmentStartDetails) = coreScope.launch { _onSegmentStart.emit(d) }
    internal fun emitSegmentError(d: SegmentErrorDetails) = coreScope.launch { _onSegmentError.emit(d) }
    internal fun emitSegmentAbort(d: SegmentAbortDetails) = coreScope.launch { _onSegmentAbort.emit(d) }
    internal fun emitPeerConnect(d: PeerDetails) = coreScope.launch { _onPeerConnect.emit(d) }
    internal fun emitPeerClose(d: PeerDetails) = coreScope.launch { _onPeerClose.emit(d) }
    internal fun emitPeerError(d: PeerErrorDetails) = coreScope.launch { _onPeerError.emit(d) }
    internal fun emitChunkDownloaded(d: ChunkDownloadedDetails) = coreScope.launch { _onChunkDownloaded.emit(d) }
    internal fun emitChunkUploaded(d: ChunkUploadedDetails) = coreScope.launch { _onChunkUploaded.emit(d) }
    internal fun emitTrackerError(d: TrackerErrorDetails) = coreScope.launch { _onTrackerError.emit(d) }
    internal fun emitTrackerWarning(d: TrackerWarningDetails) = coreScope.launch { _onTrackerWarning.emit(d) }

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
        if (!isCoreActive()) return
        val engine = engineManagerProvider() ?: return

        flowsWithNames.forEach { (eventName, flow) ->
            if (flow.subscriptionCount.value > 0) {
                engine.subscribeToP2PEvent(eventName)
            }
        }
    }
}
