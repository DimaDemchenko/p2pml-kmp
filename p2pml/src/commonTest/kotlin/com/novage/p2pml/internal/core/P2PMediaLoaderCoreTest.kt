package com.novage.p2pml.internal.core

import com.novage.p2pml.api.errors.P2PMediaLoaderErrorCode
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.api.logging.P2PLogger
import com.novage.p2pml.api.logging.P2PLogging
import com.novage.p2pml.api.playback.PlaybackListener
import com.novage.p2pml.api.playback.PlaybackProvider
import com.novage.p2pml.api.state.P2PMediaLoaderStatus
import com.novage.p2pml.internal.webview.WebViewFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class P2PMediaLoaderCoreTest {
    private var originalSink: P2PLogger? = null

    @BeforeTest
    fun setUp() {
        originalSink = P2PLogging.sink
        P2PLogging.sink = null
    }

    @AfterTest
    fun tearDown() {
        P2PLogging.sink = originalSink
    }

    @Test
    fun releaseBeforeInitializeLatchesReleased() {
        val core = P2PMediaLoaderCore()

        core.release()

        assertEquals(P2PMediaLoaderStatus.RELEASED, core.state.value.status)
        assertNull(core.state.value.error)
    }

    @Test
    fun releaseBeforeInitializeIsTerminal() {
        val core = P2PMediaLoaderCore()

        core.release()
        core.release(
            P2PMediaLoaderException(P2PMediaLoaderErrorCode.ENGINE_CRASHED, "late failure")
        )

        assertEquals(P2PMediaLoaderStatus.RELEASED, core.state.value.status)
        assertNull(core.state.value.error)
    }

    @Test
    fun failureBeforeInitializeLatchesFailedWithCause() {
        val core = P2PMediaLoaderCore()
        val failure = P2PMediaLoaderException(P2PMediaLoaderErrorCode.ENGINE_CRASHED, "boom")

        core.release(failure)

        assertEquals(P2PMediaLoaderStatus.FAILED, core.state.value.status)
        assertEquals(failure, core.state.value.error)
    }

    @Test
    fun initializeAfterReleaseThrowsInvalidStateAndKeepsState() = runTest {
        val core = P2PMediaLoaderCore()
        core.release()

        val exception = assertFailsWith<P2PMediaLoaderException> {
            core.initialize(StubPlaybackProvider(), StubWebViewFactory())
        }

        assertEquals(P2PMediaLoaderErrorCode.INVALID_STATE, exception.code)
        assertEquals(P2PMediaLoaderStatus.RELEASED, core.state.value.status)
        assertNull(core.state.value.error)
    }

    private class StubPlaybackProvider : PlaybackProvider {
        override fun setPlaybackListener(listener: PlaybackListener?) = Unit
    }

    private class StubWebViewFactory : WebViewFactory {
        override fun createHeadlessWebView(events: P2PEvents, onFatalError: (P2PMediaLoaderException) -> Unit) =
            throw AssertionError("initialize() must fail before creating a WebView")
    }
}
