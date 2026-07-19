package com.novage.p2pml.internal.core

import com.novage.p2pml.api.config.DynamicCoreConfig
import com.novage.p2pml.api.errors.P2PMediaLoaderErrorCode
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.api.logging.P2PLogger
import com.novage.p2pml.api.logging.P2PLogging
import com.novage.p2pml.api.playback.PlaybackListener
import com.novage.p2pml.api.playback.PlaybackProvider
import com.novage.p2pml.api.state.P2PMediaLoaderStatus
import com.novage.p2pml.internal.webview.HeadlessWebView
import com.novage.p2pml.internal.webview.WebViewFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancellationDuringActivationTailIsTerminal() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val core = P2PMediaLoaderCore()
            // A cached config is drained in the non-suspending activation tail, right after the
            // session is created and before the state latches ACTIVE. Cancelling the initializing
            // job from inside that drain lands cancellation in the tail window deterministically:
            // no suspension point remains, so it only surfaces when initialize() returns.
            core.applyDynamicConfig(DynamicCoreConfig())

            var initJob: Job? = null
            val jobAssigned = CompletableDeferred<Unit>()
            val webView = FakeBootingWebView(
                awaitBeforeLoad = jobAssigned,
                onDynamicConfigEvaluated = { initJob?.cancel() }
            )
            initJob = launch(Dispatchers.Default) {
                core.initialize(StubPlaybackProvider(), FakeWebViewFactory(webView))
            }
            jobAssigned.complete(Unit)
            initJob.join()

            assertTrue(initJob.isCancelled)
            assertEquals(P2PMediaLoaderStatus.RELEASED, core.state.value.status)
            assertNull(core.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun bootFailurePropagatesToCallerAndLatchesFailed() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val core = P2PMediaLoaderCore()

            val exception = assertFailsWith<P2PMediaLoaderException> {
                core.initialize(StubPlaybackProvider(), ThrowingWebViewFactory())
            }

            assertEquals(P2PMediaLoaderErrorCode.ENGINE_INIT_FAILED, exception.code)
            assertTrue(exception.cause is WebViewCreationException)
            assertEquals(P2PMediaLoaderStatus.FAILED, core.state.value.status)
            assertEquals(exception, core.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun initializeReachesActiveAndReleaseIsTerminal() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val core = P2PMediaLoaderCore()
            val webView = FakeBootingWebView(
                awaitBeforeLoad = CompletableDeferred(Unit),
                onDynamicConfigEvaluated = {}
            )

            core.initialize(StubPlaybackProvider(), FakeWebViewFactory(webView))

            assertEquals(P2PMediaLoaderStatus.ACTIVE, core.state.value.status)
            val playbackUrl = core.createPlaybackUrl("https://origin.example.com/live/master.m3u8")
            assertTrue(playbackUrl.startsWith("http://127.0.0.1:"))

            core.release()

            assertEquals(P2PMediaLoaderStatus.RELEASED, core.state.value.status)
            assertNull(core.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun releaseCompletesEventStreams() = runTest {
        val core = P2PMediaLoaderCore()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            core.p2pEvents.onPeerConnect.collect {}
        }
        assertFalse(collector.isCompleted)

        core.release()
        collector.join()

        assertTrue(collector.isCompleted)
        assertFalse(collector.isCancelled)
    }

    private class StubPlaybackProvider : PlaybackProvider {
        override fun setPlaybackListener(listener: PlaybackListener?) = Unit
    }

    /** Arbitrary non-SDK exception: must surface at the initialize call site mapped to ENGINE_INIT_FAILED. */
    private class WebViewCreationException : Exception("WebView creation exploded")

    private class ThrowingWebViewFactory : WebViewFactory {
        override fun createHeadlessWebView(events: P2PEvents, onFatalError: (P2PMediaLoaderException) -> Unit) =
            throw WebViewCreationException()
    }

    private class StubWebViewFactory : WebViewFactory {
        override fun createHeadlessWebView(events: P2PEvents, onFatalError: (P2PMediaLoaderException) -> Unit) =
            throw AssertionError("initialize() must fail before creating a WebView")
    }

    /** Completes the boot protocol instantly; [onDynamicConfigEvaluated] fires in the activation tail. */
    private class FakeBootingWebView(
        private val awaitBeforeLoad: CompletableDeferred<Unit>,
        private val onDynamicConfigEvaluated: () -> Unit
    ) : HeadlessWebView {
        override suspend fun loadUrlAndWait(url: String) = awaitBeforeLoad.await()

        override fun evaluateJavascript(script: String) {
            if ("applyDynamicP2PCoreConfig" in script) onDynamicConfigEvaluated()
        }

        override suspend fun initCoreAndWait(script: String) = Unit

        override fun destroy() = Unit
    }

    private class FakeWebViewFactory(private val webView: HeadlessWebView) : WebViewFactory {
        override fun createHeadlessWebView(events: P2PEvents, onFatalError: (P2PMediaLoaderException) -> Unit) = webView
    }
}
