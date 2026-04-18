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
    private fun <T> createFlow(capacity: Int = 64) = MutableSharedFlow<T>(
        extraBufferCapacity = capacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _onSegmentLoaded = createFlow<SegmentLoadDetails>()
    val onSegmentLoaded = _onSegmentLoaded.asSharedFlow()

    private val _onSegmentStart = createFlow<SegmentStartDetails>()
    val onSegmentStart = _onSegmentStart.asSharedFlow()

    private val _onSegmentError = createFlow<SegmentErrorDetails>()
    val onSegmentError = _onSegmentError.asSharedFlow()

    private val _onSegmentAbort = createFlow<SegmentAbortDetails>()
    val onSegmentAbort = _onSegmentAbort.asSharedFlow()

    private val _onPeerConnect = createFlow<PeerDetails>()
    val onPeerConnect = _onPeerConnect.asSharedFlow()

    private val _onPeerClose = createFlow<PeerDetails>()
    val onPeerClose = _onPeerClose.asSharedFlow()

    private val _onPeerError = createFlow<PeerErrorDetails>()
    val onPeerError = _onPeerError.asSharedFlow()

    private val _onTrackerError = createFlow<TrackerErrorDetails>()
    val onTrackerError = _onTrackerError.asSharedFlow()

    private val _onTrackerWarning = createFlow<TrackerWarningDetails>()
    val onTrackerWarning = _onTrackerWarning.asSharedFlow()

    private val _onChunkDownloaded = createFlow<ChunkDownloadedDetails>(capacity = 256)
    val onChunkDownloaded = _onChunkDownloaded.asSharedFlow()

    private val _onChunkUploaded = createFlow<ChunkUploadedDetails>(capacity = 256)
    val onChunkUploaded = _onChunkUploaded.asSharedFlow()

    internal fun emitSegmentLoaded(d: SegmentLoadDetails) = _onSegmentLoaded.tryEmit(d)
    internal fun emitSegmentStart(d: SegmentStartDetails) = _onSegmentStart.tryEmit(d)
    internal fun emitSegmentError(d: SegmentErrorDetails) = _onSegmentError.tryEmit(d)
    internal fun emitSegmentAbort(d: SegmentAbortDetails) = _onSegmentAbort.tryEmit(d)
    internal fun emitPeerConnect(d: PeerDetails) = _onPeerConnect.tryEmit(d)
    internal fun emitPeerClose(d: PeerDetails) = _onPeerClose.tryEmit(d)
    internal fun emitPeerError(d: PeerErrorDetails) = _onPeerError.tryEmit(d)
    internal fun emitChunkDownloaded(d: ChunkDownloadedDetails) = _onChunkDownloaded.tryEmit(d)
    internal fun emitChunkUploaded(d: ChunkUploadedDetails) = _onChunkUploaded.tryEmit(d)
    internal fun emitTrackerError(d: TrackerErrorDetails) = _onTrackerError.tryEmit(d)
    internal fun emitTrackerWarning(d: TrackerWarningDetails) = _onTrackerWarning.tryEmit(d)

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
