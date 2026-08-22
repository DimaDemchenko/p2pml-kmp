package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.ByteRange
import com.novage.p2pml.api.events.DownloadSource
import com.novage.p2pml.api.events.JsError
import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.api.events.StreamType
import com.novage.p2pml.api.logging.LogLevel
import com.novage.p2pml.api.logging.P2PLogger
import com.novage.p2pml.api.logging.P2PLogging
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Golden-payload contract tests for the engine event wire format (engine 4.0.0).
 *
 * The fixtures mirror the exact JSON the bridge page produces (formatEventPayload +
 * serializeJsError in src/assets/index.html) for the payload shapes the bundled engine
 * emits. If a model or the bridge changes shape, these tests fail instead of events
 * being silently dropped at runtime.
 */
class EngineEventPayloadContractTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val events = P2PEvents(
        coreScope = scope,
        onSubscribe = {},
        onUnsubscribe = {},
        isCoreActive = { true }
    )
    private val router = WebViewMessageRouter(events = events)
    private val originalSink = P2PLogging.sink

    @AfterTest
    fun tearDown() {
        scope.cancel()
        P2PLogging.sink = originalSink
    }

    private fun <T> collect(flow: Flow<T>): List<T> {
        val sink = mutableListOf<T>()
        scope.launch { flow.collect { sink.add(it) } }
        return sink
    }

    @Test
    fun decodesSegmentLoaded() {
        val collected = collect(events.onSegmentLoaded)

        router.handleMessage(
            """
            {"type":"onSegmentLoaded","payload":{
                "segment":{"runtimeId":"https://cdn.example.com/seg42.ts","externalId":42,
                    "url":"https://cdn.example.com/seg42.ts","startTime":10.0,"endTime":16.0},
                "bytesLength":524288,
                "downloadSource":"p2p",
                "peerId":"peer-1",
                "infoHash":"aabbccdd",
                "streamType":"main"}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertEquals(42L, details.segment.externalId)
        assertEquals(524288, details.bytesLength)
        assertEquals(DownloadSource.P2P, details.downloadSource)
        assertEquals("peer-1", details.peerId)
        assertEquals("aabbccdd", details.infoHash)
        assertEquals(StreamType.MAIN, details.streamType)
    }

    @Test
    fun decodesSegmentStart() {
        val collected = collect(events.onSegmentStart)

        router.handleMessage(
            """
            {"type":"onSegmentStart","payload":{
                "segment":{"runtimeId":"https://cdn.example.com/seg1.ts","externalId":1,
                    "url":"https://cdn.example.com/seg1.ts","startTime":0.0,"endTime":6.0},
                "downloadSource":"http",
                "infoHash":"aabbccdd",
                "streamType":"main"}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertNull(details.peerId)
        assertNull(details.segment.byteRange)
        assertEquals("aabbccdd", details.infoHash)
        assertEquals(StreamType.MAIN, details.streamType)
    }

    @Test
    fun decodesSegmentErrorWithoutPeerIdAndByteRange() {
        val collected = collect(events.onSegmentError)

        // http failure: the engine emits peerId as undefined and this segment has no byte
        // range, so both keys are absent from the JSON entirely.
        router.handleMessage(
            """
            {"type":"onSegmentError","payload":{
                "segment":{"runtimeId":"https://cdn.example.com/seg42.ts","externalId":42,
                    "url":"https://cdn.example.com/seg42.ts","startTime":10.0,"endTime":16.0},
                "error":{"message":"Request failed","type":"http-error"},
                "downloadSource":"http",
                "infoHash":"aabbccdd",
                "streamType":"main"}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertEquals(JsError(message = "Request failed", type = "http-error"), details.error)
        assertEquals(DownloadSource.HTTP, details.downloadSource)
        assertNull(details.peerId)
        assertNull(details.segment.byteRange)
    }

    @Test
    fun decodesSegmentErrorWithPeerIdAndByteRange() {
        val collected = collect(events.onSegmentError)

        router.handleMessage(
            """
            {"type":"onSegmentError","payload":{
                "segment":{"runtimeId":"https://cdn.example.com/seg7.ts|0-499","externalId":7,
                    "url":"https://cdn.example.com/seg7.ts","byteRange":{"start":0,"end":499},
                    "startTime":0.0,"endTime":6.0},
                "error":{"message":"peer timeout","type":"bytes-receiving-timeout"},
                "downloadSource":"p2p",
                "peerId":"peer-1",
                "infoHash":"aabbccdd",
                "streamType":"secondary"}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertEquals("peer-1", details.peerId)
        assertEquals(DownloadSource.P2P, details.downloadSource)
        assertEquals(ByteRange(0, 499), details.segment.byteRange)
    }

    @Test
    fun decodesSegmentAbortWithoutDownloadSource() {
        val collected = collect(events.onSegmentAbort)

        // Abort before any download attempt: the engine emits
        // downloadSource: this.currentAttempt?.downloadSource, so the key is absent.
        router.handleMessage(
            """
            {"type":"onSegmentAbort","payload":{
                "segment":{"runtimeId":"https://cdn.example.com/seg9.ts","externalId":9,
                    "url":"https://cdn.example.com/seg9.ts","startTime":54.0,"endTime":60.0},
                "infoHash":"aabbccdd",
                "streamType":"main"}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertNull(details.downloadSource)
        assertNull(details.peerId)
        assertEquals("aabbccdd", details.infoHash)
    }

    @Test
    fun decodesPeerConnect() {
        val collected = collect(events.onPeerConnect)

        router.handleMessage(
            """{"type":"onPeerConnect","payload":{"peerId":"abcd1234","infoHash":"aabbccdd","streamType":"main"}}"""
        )

        assertEquals(1, collected.size)
        assertEquals("aabbccdd", collected.single().infoHash)
    }

    @Test
    fun decodesPeerError() {
        val collected = collect(events.onPeerError)

        router.handleMessage(
            """
            {"type":"onPeerError","payload":{
                "peerId":"abcd1234",
                "infoHash":"aabbccdd",
                "streamType":"main",
                "error":{"message":"Connection lost","type":"connection-lost"}}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertEquals("abcd1234", details.peerId)
        assertEquals(JsError(message = "Connection lost", type = "connection-lost"), details.error)
    }

    @Test
    fun decodesPeerWarning() {
        val collected = collect(events.onPeerWarning)

        router.handleMessage(
            """
            {"type":"onPeerWarning","payload":{
                "peerId":"abcd1234",
                "infoHash":"aabbccdd",
                "streamType":"main",
                "warning":{"message":"peer timeout strike","type":"timeout-strike"}}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertEquals("abcd1234", details.peerId)
        assertEquals(JsError(message = "peer timeout strike", type = "timeout-strike"), details.warning)
    }

    @Test
    fun decodesPeerConnectError() {
        val collected = collect(events.onPeerConnectError)

        router.handleMessage(
            """
            {"type":"onPeerConnectError","payload":{
                "peerId":"abcd1234",
                "infoHash":"aabbccdd",
                "streamType":"main",
                "trackerUrl":"wss://tracker.example.com",
                "error":{"message":"connection failed","type":"connection-failed"}}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertEquals("wss://tracker.example.com", details.trackerUrl)
        assertEquals(JsError(message = "connection failed", type = "connection-failed"), details.error)
    }

    @Test
    fun decodesTrackerWarning() {
        val collected = collect(events.onTrackerWarning)

        router.handleMessage(
            """
            {"type":"onTrackerWarning","payload":{
                "trackerUrl":"wss://tracker.example.com",
                "infoHash":"aabbccdd",
                "streamType":"main",
                "warning":{"message":"tracker warning","stack":"Error: tracker warning","type":"offer-failed"}}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertEquals("wss://tracker.example.com", details.trackerUrl)
        assertEquals(
            JsError(message = "tracker warning", stack = "Error: tracker warning", type = "offer-failed"),
            details.warning
        )
    }

    @Test
    fun decodesTrackerError() {
        val collected = collect(events.onTrackerError)

        router.handleMessage(
            """
            {"type":"onTrackerError","payload":{
                "trackerUrl":"wss://tracker.example.com",
                "infoHash":"aabbccdd",
                "streamType":"main",
                "error":{"message":"tracker unreachable","type":"announce-failed"}}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertEquals("aabbccdd", details.infoHash)
        assertEquals(JsError(message = "tracker unreachable", type = "announce-failed"), details.error)
    }

    @Test
    fun decodesStreamRegistrationError() {
        val collected = collect(events.onStreamRegistrationError)

        router.handleMessage(
            """
            {"type":"onStreamRegistrationError","payload":{
                "runtimeId":"V:0",
                "streamType":"main",
                "properties":{
                    "bitrate":2400000,
                    "codecs":"avc1.640028,mp4a.40.2",
                    "width":1280,
                    "height":720,
                    "frameRate":"30",
                    "videoRange":"SDR"},
                "error":{"message":"Swarm ID is not resolvable"}}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertEquals("V:0", details.runtimeId)
        assertEquals(StreamType.MAIN, details.streamType)
        assertEquals(2400000, details.properties.bitrate)
        assertEquals("avc1.640028,mp4a.40.2", details.properties.codecs)
        assertEquals(1280, details.properties.width)
        assertEquals(720, details.properties.height)
        assertEquals("30", details.properties.frameRate)
        assertEquals("SDR", details.properties.videoRange)
        assertNull(details.properties.language)
        assertEquals(JsError(message = "Swarm ID is not resolvable"), details.error)
    }

    @Test
    fun decodesStreamRegistrationErrorForAudioRendition() {
        val collected = collect(events.onStreamRegistrationError)

        // Audio renditions carry a disjoint property set from variants; absent keys must decode
        // to null rather than failing the whole payload.
        router.handleMessage(
            """
            {"type":"onStreamRegistrationError","payload":{
                "runtimeId":"A:audio-en",
                "streamType":"secondary",
                "properties":{"language":"en-US","channels":"6/JOC","name":"English"},
                "error":{"message":"duplicate stream swarm ID","type":"registration-conflict"}}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertEquals(StreamType.SECONDARY, details.streamType)
        assertEquals("en-US", details.properties.language)
        assertEquals("6/JOC", details.properties.channels)
        assertEquals("English", details.properties.name)
        assertNull(details.properties.bitrate)
        assertEquals("registration-conflict", details.error.type)
    }

    @Test
    fun chunkEventFromGenericDispatchIsReportedNotSilentlyDropped() {
        val warnings = mutableListOf<String>()
        P2PLogging.sink = P2PLogger { level, _, message, _ ->
            if (level == LogLevel.WARN) warnings.add(message)
        }
        val collected = collect(events.onChunkDownloaded)

        // Chunk events are delivered through the typed native bridge, never the generic router. A
        // name-based dispatch to a chunk channel must be reported as unroutable, not silently dropped.
        router.handleMessage(
            """
            {"type":"onChunkDownloaded","payload":{
                "bytesLength":1024,"downloadSource":"p2p","infoHash":"aabbccdd","streamType":"main"}}
            """.trimIndent()
        )

        assertEquals(0, collected.size)
        assertTrue(warnings.any { it.contains("No dispatcher found for event: onChunkDownloaded") })
    }
}
