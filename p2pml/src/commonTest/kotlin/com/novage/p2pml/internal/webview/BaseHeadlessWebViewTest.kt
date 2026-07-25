package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.errors.P2PMediaLoaderErrorCode
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Freezes the shared state machine in [BaseHeadlessWebView]. The fake runs every hook synchronously
 * (main-thread dispatch is a direct call) and exposes the bridge/navigation callbacks the real
 * platform subclasses wire up, so the guard, resume, error-routing and teardown behaviour can be
 * exercised without a real WebView.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseHeadlessWebViewTest {
    private class FakeHeadlessWebView(
        onFatalError: (P2PMediaLoaderException) -> Unit = {},
        private var alive: Boolean = true,
        var rejection: Exception? = null
    ) : BaseHeadlessWebView(onFatalError) {
        val loads = mutableListOf<String>()
        val initScripts = mutableListOf<String>()
        val fireAndForget = mutableListOf<String>()
        var teardownCount = 0
            private set
        var stopLoadingCount = 0
            private set

        override fun postToMainThread(block: () -> Unit) = block()
        override fun isWebViewAlive(): Boolean = alive

        override fun startLoad(url: String): Exception? {
            rejection?.let { return it }
            loads += url
            return null
        }

        override fun stopLoading() {
            stopLoadingCount++
        }

        override fun evaluateFireAndForget(script: String) {
            fireAndForget += script
        }

        override fun evaluateInitScript(script: String) {
            initScripts += script
        }

        override fun teardownWebView() {
            teardownCount++
            alive = false
        }

        // The real subclasses invoke these from their JS bridge / navigation-error callbacks.
        fun emitPageReady() = notifyPageReady()
        fun emitCoreInitResult(errorMessage: String?) = handleCoreInitResult(errorMessage)
        fun emitError(msg: String) = handleError(msg)
    }

    @Test
    fun loadFailsWhenWebViewNotAlive() = runTest(UnconfinedTestDispatcher()) {
        val webView = FakeHeadlessWebView(alive = false)

        val ex = assertFailsWith<IllegalStateException> { webView.loadUrlAndWait("https://x") }

        assertEquals("WebView is destroyed", ex.message)
        assertTrue(webView.loads.isEmpty())
    }

    @Test
    fun loadRejectionFailsWithoutLoading() = runTest(UnconfinedTestDispatcher()) {
        val rejection = IllegalArgumentException("Invalid URL: bad")
        val webView = FakeHeadlessWebView(rejection = rejection)

        val ex = assertFailsWith<IllegalArgumentException> { webView.loadUrlAndWait("bad") }

        assertEquals("Invalid URL: bad", ex.message)
        assertTrue(webView.loads.isEmpty())
    }

    @Test
    fun loadRejectionRollsBackStateSoNextLoadCanStart() = runTest(UnconfinedTestDispatcher()) {
        val webView = FakeHeadlessWebView(rejection = IllegalArgumentException("Invalid URL: bad"))
        assertFailsWith<IllegalArgumentException> { webView.loadUrlAndWait("bad") }

        webView.rejection = null
        launch { webView.loadUrlAndWait("https://ok") }

        assertEquals(listOf("https://ok"), webView.loads)
        webView.emitPageReady()
    }

    @Test
    fun secondConcurrentLoadIsRejected() = runTest(UnconfinedTestDispatcher()) {
        val webView = FakeHeadlessWebView()
        val first = launch { webView.loadUrlAndWait("https://first") }

        val ex = assertFailsWith<IllegalStateException> { webView.loadUrlAndWait("https://second") }

        assertEquals("A load is already in progress", ex.message)
        assertEquals(listOf("https://first"), webView.loads)
        first.cancel()
    }

    @Test
    fun pageReadyResumesPendingLoad() = runTest(UnconfinedTestDispatcher()) {
        val webView = FakeHeadlessWebView()
        var completed = false
        launch {
            webView.loadUrlAndWait("https://x")
            completed = true
        }

        assertEquals(listOf("https://x"), webView.loads)
        assertTrue(!completed)

        webView.emitPageReady()

        assertTrue(completed)
    }

    @Test
    fun errorFailsPendingLoadWithLoadFailedCode() = runTest(UnconfinedTestDispatcher()) {
        val webView = FakeHeadlessWebView()
        var thrown: Throwable? = null
        launch { thrown = runCatching { webView.loadUrlAndWait("https://x") }.exceptionOrNull() }

        webView.emitError("boom")

        val ex = assertIs<P2PMediaLoaderException>(thrown)
        assertEquals(P2PMediaLoaderErrorCode.ENGINE_LOAD_FAILED, ex.code)
        assertEquals("boom", ex.message)
    }

    @Test
    fun errorFailsPendingInitWithCrashedCode() = runTest(UnconfinedTestDispatcher()) {
        val webView = FakeHeadlessWebView()
        var thrown: Throwable? = null
        launch { thrown = runCatching { webView.initCoreAndWait("script") }.exceptionOrNull() }

        assertEquals(listOf("script"), webView.initScripts)

        webView.emitError("crash")

        val ex = assertIs<P2PMediaLoaderException>(thrown)
        assertEquals(P2PMediaLoaderErrorCode.ENGINE_CRASHED, ex.code)
    }

    @Test
    fun errorWithNoWaiterReportsFatal() = runTest(UnconfinedTestDispatcher()) {
        val fatals = mutableListOf<P2PMediaLoaderException>()
        val webView = FakeHeadlessWebView(onFatalError = { fatals += it })

        webView.emitError("spontaneous crash")

        assertEquals(1, fatals.size)
        assertEquals(P2PMediaLoaderErrorCode.ENGINE_CRASHED, fatals.single().code)
    }

    @Test
    fun errorAfterDestroyIsNotReportedAsFatal() = runTest(UnconfinedTestDispatcher()) {
        val fatals = mutableListOf<P2PMediaLoaderException>()
        val webView = FakeHeadlessWebView(onFatalError = { fatals += it })

        webView.destroy()
        webView.emitError("late teardown callback")

        assertTrue(fatals.isEmpty())
    }

    @Test
    fun coreInitSuccessResumesPendingInit() = runTest(UnconfinedTestDispatcher()) {
        val webView = FakeHeadlessWebView()
        var completed = false
        launch {
            webView.initCoreAndWait("script")
            completed = true
        }

        webView.emitCoreInitResult(null)

        assertTrue(completed)
    }

    @Test
    fun coreInitFailureFailsPendingInitWithInitFailedCode() = runTest(UnconfinedTestDispatcher()) {
        val webView = FakeHeadlessWebView()
        var thrown: Throwable? = null
        launch { thrown = runCatching { webView.initCoreAndWait("script") }.exceptionOrNull() }

        webView.emitCoreInitResult("init boom")

        val ex = assertIs<P2PMediaLoaderException>(thrown)
        assertEquals(P2PMediaLoaderErrorCode.ENGINE_INIT_FAILED, ex.code)
        assertEquals("init boom", ex.message)
    }

    @Test
    fun secondConcurrentInitIsRejected() = runTest(UnconfinedTestDispatcher()) {
        val webView = FakeHeadlessWebView()
        val first = launch { webView.initCoreAndWait("first") }

        val ex = assertFailsWith<IllegalStateException> { webView.initCoreAndWait("second") }

        assertEquals("A core init is already in progress", ex.message)
        first.cancel()
    }

    @Test
    fun destroyCancelsPendingLoadAndTearsDown() = runTest(UnconfinedTestDispatcher()) {
        val webView = FakeHeadlessWebView()
        val job = launch { webView.loadUrlAndWait("https://x") }
        assertTrue(job.isActive)

        webView.destroy()

        assertTrue(job.isCancelled)
        assertEquals(1, webView.stopLoadingCount)
        assertEquals(1, webView.teardownCount)
    }

    @Test
    fun evaluateJavascriptDelegatesToFireAndForgetHook() = runTest(UnconfinedTestDispatcher()) {
        val webView = FakeHeadlessWebView()

        webView.evaluateJavascript("doStuff()")

        assertEquals(listOf("doStuff()"), webView.fireAndForget)
    }
}
