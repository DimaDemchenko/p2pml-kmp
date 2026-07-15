package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.ByteRange
import com.novage.p2pml.api.events.DownloadSource
import com.novage.p2pml.api.events.JsError
import com.novage.p2pml.api.events.P2PEvents
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Golden-payload contract tests for the engine event wire format.
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

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun <T> collect(flow: SharedFlow<T>): List<T> {
        val sink = mutableListOf<T>()
        scope.launch { flow.collect { sink.add(it) } }
        return sink
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
                "streamType":"main"}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertEquals(JsError(message = "Request failed", type = "http-error"), details.error)
        assertEquals(DownloadSource.HTTP, details.downloadSource)
        assertNull(details.peerId)
        assertNull(details.segment.byteRange)
        assertEquals(42L, details.segment.externalId)
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
                "error":{"message":"peer timeout","type":"request-timeout"},
                "downloadSource":"p2p",
                "peerId":"peer-1",
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
    fun decodesPeerError() {
        val collected = collect(events.onPeerError)

        router.handleMessage(
            """
            {"type":"onPeerError","payload":{
                "peerId":"abcd1234",
                "streamType":"main",
                "error":{"message":"Connection failed","type":"ERR_CONNECTION_FAILURE"}}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertEquals("abcd1234", details.peerId)
        assertEquals(JsError(message = "Connection failed", type = "ERR_CONNECTION_FAILURE"), details.error)
    }

    @Test
    fun decodesTrackerWarning() {
        val collected = collect(events.onTrackerWarning)

        router.handleMessage(
            """
            {"type":"onTrackerWarning","payload":{
                "streamType":"main",
                "warning":{"message":"tracker warning","stack":"Error: tracker warning"}}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        val details = collected.single()
        assertEquals("main", details.streamType)
        assertEquals(JsError(message = "tracker warning", stack = "Error: tracker warning"), details.warning)
    }

    @Test
    fun decodesTrackerError() {
        val collected = collect(events.onTrackerError)

        router.handleMessage(
            """
            {"type":"onTrackerError","payload":{
                "streamType":"main",
                "error":{"message":"tracker unreachable"}}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        assertEquals(JsError(message = "tracker unreachable"), collected.single().error)
    }

    @Test
    fun decodesSegmentStartWithoutByteRange() {
        val collected = collect(events.onSegmentStart)

        router.handleMessage(
            """
            {"type":"onSegmentStart","payload":{
                "segment":{"runtimeId":"https://cdn.example.com/seg1.ts","externalId":1,
                    "url":"https://cdn.example.com/seg1.ts","startTime":0.0,"endTime":6.0},
                "downloadSource":"http"}}
            """.trimIndent()
        )

        assertEquals(1, collected.size)
        assertNull(collected.single().segment.byteRange)
    }
}
