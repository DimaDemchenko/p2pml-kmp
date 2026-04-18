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
import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class P2PEventRegistry internal constructor(
    private val coreScope: CoroutineScope,
    private val engineManagerProvider: () -> P2PEngine?,
    private val isCoreActive: () -> Boolean
) {
    private val logger = CoreLogger("P2PEventRegistry")

    private val dispatchersElement = mutableMapOf<String, (JsonElement, Json) -> Unit>()
    private val dispatchersString = mutableMapOf<String, (String, Json) -> Unit>()

    private inline fun <reified T> createAndRegisterFlow(eventName: String, capacity: Int = 64): MutableSharedFlow<T> {
        val flow = MutableSharedFlow<T>(
            extraBufferCapacity = capacity,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        dispatchersElement[eventName] = { payload, json ->
            flow.tryEmit(json.decodeFromJsonElement<T>(payload))
        }
        dispatchersString[eventName] = { payloadStr, json ->
            flow.tryEmit(json.decodeFromString<T>(payloadStr))
        }
        return flow
    }

    private val _onSegmentLoaded = createAndRegisterFlow<SegmentLoadDetails>("onSegmentLoaded")
    val onSegmentLoaded = _onSegmentLoaded.asSharedFlow()

    private val _onSegmentStart = createAndRegisterFlow<SegmentStartDetails>("onSegmentStart")
    val onSegmentStart = _onSegmentStart.asSharedFlow()

    private val _onSegmentError = createAndRegisterFlow<SegmentErrorDetails>("onSegmentError")
    val onSegmentError = _onSegmentError.asSharedFlow()

    private val _onSegmentAbort = createAndRegisterFlow<SegmentAbortDetails>("onSegmentAbort")
    val onSegmentAbort = _onSegmentAbort.asSharedFlow()

    private val _onPeerConnect = createAndRegisterFlow<PeerDetails>("onPeerConnect")
    val onPeerConnect = _onPeerConnect.asSharedFlow()

    private val _onPeerClose = createAndRegisterFlow<PeerDetails>("onPeerClose")
    val onPeerClose = _onPeerClose.asSharedFlow()

    private val _onPeerError = createAndRegisterFlow<PeerErrorDetails>("onPeerError")
    val onPeerError = _onPeerError.asSharedFlow()

    private val _onTrackerError = createAndRegisterFlow<TrackerErrorDetails>("onTrackerError")
    val onTrackerError = _onTrackerError.asSharedFlow()

    private val _onTrackerWarning = createAndRegisterFlow<TrackerWarningDetails>("onTrackerWarning")
    val onTrackerWarning = _onTrackerWarning.asSharedFlow()

    private val _onChunkDownloaded = createAndRegisterFlow<ChunkDownloadedDetails>("onChunkDownloaded", capacity = 256)
    val onChunkDownloaded = _onChunkDownloaded.asSharedFlow()

    private val _onChunkUploaded = createAndRegisterFlow<ChunkUploadedDetails>("onChunkUploaded", capacity = 256)
    val onChunkUploaded = _onChunkUploaded.asSharedFlow()

    internal fun emitChunkDownloaded(d: ChunkDownloadedDetails) = _onChunkDownloaded.tryEmit(d)
    internal fun emitChunkUploaded(d: ChunkUploadedDetails) = _onChunkUploaded.tryEmit(d)

    fun dispatchEventFromJsonElement(eventName: String, payload: JsonElement, json: Json) {
        val dispatcher = dispatchersElement[eventName]
        if (dispatcher != null) {
            dispatcher.invoke(payload, json)
        } else {
            logger.w { "No dispatcher found for event: $eventName" }
        }
    }

    fun dispatchEventFromJsonString(eventName: String, payload: String, json: Json) {
        val dispatcher = dispatchersString[eventName]
        if (dispatcher != null) {
            dispatcher.invoke(payload, json)
        } else {
            logger.w { "No dispatcher found for event: $eventName" }
        }
    }

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
