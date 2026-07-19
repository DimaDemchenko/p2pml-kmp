package com.novage.p2pml.api.events

import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Streams of P2P engine events as hot [Flow]s bounded by the loader's lifecycle.
 *
 * Collecting a flow has a side effect: the first collector of an event subscribes the JS engine
 * to it, and when the last collector leaves, the engine is unsubscribed — a flow nobody collects
 * never emits. Subscriptions made before the loader becomes active are forwarded to the engine
 * once it starts.
 *
 * Every stream completes normally once the loader reaches a terminal state (released or failed):
 * active collectors stop, and collectors arriving after that complete immediately without
 * emitting. A Swift `for await` loop over an event (via SKIE) therefore finishes when the loader
 * is released instead of suspending forever; in Kotlin, `collect` returns.
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

    private val coreJob = checkNotNull(coreScope.coroutineContext[Job]) { "coreScope must have a Job" }

    private class EventChannel<T>(
        val name: String,
        val flow: MutableSharedFlow<T>,
        private val decode: ((JsonElement, Json) -> T)? = null
    ) {
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

    val onSegmentLoaded: Flow<SegmentLoadDetails> = segmentLoaded.untilCoreShutdown()
    val onSegmentStart: Flow<SegmentStartDetails> = segmentStart.untilCoreShutdown()
    val onSegmentError: Flow<SegmentErrorDetails> = segmentError.untilCoreShutdown()
    val onSegmentAbort: Flow<SegmentAbortDetails> = segmentAbort.untilCoreShutdown()
    val onPeerConnect: Flow<PeerDetails> = peerConnect.untilCoreShutdown()
    val onPeerConnectError: Flow<PeerConnectErrorDetails> = peerConnectError.untilCoreShutdown()
    val onPeerClose: Flow<PeerDetails> = peerClose.untilCoreShutdown()
    val onPeerError: Flow<PeerErrorDetails> = peerError.untilCoreShutdown()
    val onPeerWarning: Flow<PeerWarningDetails> = peerWarning.untilCoreShutdown()
    val onTrackerError: Flow<TrackerErrorDetails> = trackerError.untilCoreShutdown()
    val onTrackerWarning: Flow<TrackerWarningDetails> = trackerWarning.untilCoreShutdown()
    val onChunkDownloaded: Flow<ChunkDownloadedDetails> = chunkDownloaded.untilCoreShutdown()
    val onChunkUploaded: Flow<ChunkUploadedDetails> = chunkUploaded.untilCoreShutdown()

    private fun <T> EventChannel<T>.untilCoreShutdown(): Flow<T> = channelFlow {
        val forwarder = launch { flow.collect { send(it) } }
        coreJob.join()
        forwarder.cancel()
    }

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
