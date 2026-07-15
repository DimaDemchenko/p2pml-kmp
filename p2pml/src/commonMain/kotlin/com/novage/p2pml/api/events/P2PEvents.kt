package com.novage.p2pml.api.events

import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Streams of P2P engine events as hot [SharedFlow]s.
 *
 * Collecting a flow has a side effect: the first collector of an event subscribes the JS engine
 * to it, and when the last collector leaves, the engine is unsubscribed — a flow nobody collects
 * never emits. Subscriptions made before the loader becomes active are forwarded to the engine
 * once it starts.
 *
 * Delivery uses a bounded buffer with [BufferOverflow.DROP_OLDEST]: a slow collector loses the
 * oldest pending events instead of stalling the bridge. [onChunkDownloaded] and [onChunkUploaded]
 * are high-frequency — consume them promptly if you aggregate transfer statistics.
 */
class P2PEvents internal constructor(
    private val coreScope: CoroutineScope,
    private val onSubscribe: (String) -> Unit,
    private val onUnsubscribe: (String) -> Unit,
    private val isCoreActive: () -> Boolean
) {
    private val logger = CoreLogger("P2PEvents")

    private class EventChannel<T>(
        val name: String,
        val flow: MutableSharedFlow<T>,
        private val decode: ((JsonElement, Json) -> T)? = null
    ) {
        val shared: SharedFlow<T> = flow.asSharedFlow()

        fun emitJson(payload: JsonElement, json: Json) {
            val decoder = decode ?: return
            flow.tryEmit(decoder(payload, json))
        }
    }

    private fun <T> newFlow(capacity: Int): MutableSharedFlow<T> = MutableSharedFlow(
        extraBufferCapacity = capacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private inline fun <reified T> jsonChannel(name: String, capacity: Int = 64): EventChannel<T> =
        EventChannel(name, newFlow(capacity)) { payload, json -> json.decodeFromJsonElement<T>(payload) }

    private fun <T> directChannel(name: String, capacity: Int): EventChannel<T> = EventChannel(name, newFlow(capacity))

    private val segmentLoaded = jsonChannel<SegmentLoadDetails>("onSegmentLoaded")
    private val segmentStart = jsonChannel<SegmentStartDetails>("onSegmentStart")
    private val segmentError = jsonChannel<SegmentErrorDetails>("onSegmentError")
    private val segmentAbort = jsonChannel<SegmentAbortDetails>("onSegmentAbort")
    private val peerConnect = jsonChannel<PeerDetails>("onPeerConnect")
    private val peerConnectError = jsonChannel<PeerConnectErrorDetails>("onPeerConnectError")
    private val peerClose = jsonChannel<PeerDetails>("onPeerClose")
    private val peerError = jsonChannel<PeerErrorDetails>("onPeerError")
    private val peerWarning = jsonChannel<PeerWarningDetails>("onPeerWarning")
    private val trackerError = jsonChannel<TrackerErrorDetails>("onTrackerError")
    private val trackerWarning = jsonChannel<TrackerWarningDetails>("onTrackerWarning")
    private val chunkDownloaded = directChannel<ChunkDownloadedDetails>("onChunkDownloaded", capacity = 256)
    private val chunkUploaded = directChannel<ChunkUploadedDetails>("onChunkUploaded", capacity = 256)

    private val channels: List<EventChannel<*>> = listOf(
        segmentLoaded, segmentStart, segmentError, segmentAbort,
        peerConnect, peerConnectError, peerClose, peerError, peerWarning,
        chunkDownloaded, chunkUploaded,
        trackerError, trackerWarning
    )
    private val channelsByName: Map<String, EventChannel<*>> = channels.associateBy { it.name }

    val onSegmentLoaded: SharedFlow<SegmentLoadDetails> = segmentLoaded.shared
    val onSegmentStart: SharedFlow<SegmentStartDetails> = segmentStart.shared
    val onSegmentError: SharedFlow<SegmentErrorDetails> = segmentError.shared
    val onSegmentAbort: SharedFlow<SegmentAbortDetails> = segmentAbort.shared
    val onPeerConnect: SharedFlow<PeerDetails> = peerConnect.shared
    val onPeerConnectError: SharedFlow<PeerConnectErrorDetails> = peerConnectError.shared
    val onPeerClose: SharedFlow<PeerDetails> = peerClose.shared
    val onPeerError: SharedFlow<PeerErrorDetails> = peerError.shared
    val onPeerWarning: SharedFlow<PeerWarningDetails> = peerWarning.shared
    val onTrackerError: SharedFlow<TrackerErrorDetails> = trackerError.shared
    val onTrackerWarning: SharedFlow<TrackerWarningDetails> = trackerWarning.shared
    val onChunkDownloaded: SharedFlow<ChunkDownloadedDetails> = chunkDownloaded.shared
    val onChunkUploaded: SharedFlow<ChunkUploadedDetails> = chunkUploaded.shared

    internal fun emitChunkDownloaded(d: ChunkDownloadedDetails) = chunkDownloaded.flow.tryEmit(d)
    internal fun emitChunkUploaded(d: ChunkUploadedDetails) = chunkUploaded.flow.tryEmit(d)

    internal fun dispatchEvent(eventName: String, payload: JsonElement, json: Json) {
        val channel = channelsByName[eventName]
        if (channel == null) {
            logger.w { "No dispatcher found for event: $eventName" }
            return
        }
        channel.emitJson(payload, json)
    }

    init {
        channels.forEach { channel ->
            channel.flow.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .onEach { hasSubscribers ->
                    if (!isCoreActive()) return@onEach

                    if (hasSubscribers) {
                        onSubscribe(channel.name)
                    } else {
                        onUnsubscribe(channel.name)
                    }
                }
                .launchIn(coreScope)
        }
    }

    internal fun syncEarlySubscriptions() {
        if (!isCoreActive()) return

        channels.forEach { channel ->
            if (channel.flow.subscriptionCount.value > 0) {
                onSubscribe(channel.name)
            }
        }
    }
}
