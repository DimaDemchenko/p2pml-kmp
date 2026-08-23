package com.novage.p2pml.internal.session

import com.novage.p2pml.api.config.CoreConfig
import com.novage.p2pml.api.config.DynamicCoreConfig
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.api.playback.PlaybackListener
import com.novage.p2pml.api.playback.PlaybackProvider
import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.http.createHttpClient
import com.novage.p2pml.internal.parser.hls.Stream
import com.novage.p2pml.internal.parser.hls.UpdateStreamParams
import com.novage.p2pml.internal.webview.HeadlessWebView
import com.novage.p2pml.internal.webview.WebViewFactory
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

private class EngineLoadFailure : Exception("engine page could not be loaded")

/**
 * Pins the teardown stack in [P2PSessionFactory.createSession].
 *
 * Collaborators register their own teardown as they are built, so a boot that fails partway has to
 * unwind exactly the ones that exist, in reverse. The other boot-failure coverage fails inside
 * `engineProvider` — the first statement of `createSession` — so no task is registered by then and
 * the unwind runs over an empty list. Failing at the *last* step instead is what exercises it.
 */
class P2PSessionFactoryTest {

    private class RecordingPlaybackProvider(private val log: MutableList<String>) : PlaybackProvider {
        override fun setPlaybackListener(listener: PlaybackListener?) {
            log += if (listener == null) "tracker.destroy" else "tracker.attach"
        }
    }

    /** Boots far enough to register every cleanup task, then fails the page load. */
    private class LoadFailingEngine(private val log: MutableList<String>) : P2PEngine {
        override suspend fun loadUrlAndWait(url: String): Unit = throw EngineLoadFailure()

        override fun destroy() {
            log += "engine.destroy"
        }

        override suspend fun initCoreEngineAndWait(coreConfig: CoreConfig, uploadUrl: String) = Unit
        override fun requestSegmentBytes(segmentUrl: String) = Unit
        override fun sendStream(stream: UpdateStreamParams) = Unit
        override fun sendAllStreams(streams: List<Stream>) = Unit
        override fun setManifestUrl(manifestUrl: String) = Unit
        override fun applyDynamicConfig(config: DynamicCoreConfig) = Unit
        override fun subscribeToP2PEvent(eventName: String) = Unit
        override fun unsubscribeFromP2PEvent(eventName: String) = Unit
        override fun updatePlaybackInfo(positionSec: Double, speed: Float) = Unit
    }

    private class UnusedWebViewFactory : WebViewFactory {
        override fun createHeadlessWebView(
            events: P2PEvents,
            onFatalError: (P2PMediaLoaderException) -> Unit
        ): HeadlessWebView = error("engineProvider is stubbed; the factory must never be reached")
    }

    private fun events(scope: CoroutineScope) = P2PEvents(
        coreScope = scope,
        onSubscribe = {},
        onUnsubscribe = {},
        isCoreActive = { false }
    )

    @Test
    fun bootFailureUnwindsEveryRegisteredResourceInReverseOrder() = runTest {
        val log = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob())
        var client: HttpClient? = null

        val factory = P2PSessionFactory(
            coreConfig = CoreConfig(),
            onFatalError = {},
            customEngineUrl = null,
            httpClientProvider = { createHttpClient().also { client = it } },
            engineProvider = { _, _ -> LoadFailingEngine(log) }
        )

        try {
            // A real dispatcher, as in production, where the factory runs on the core's
            // Dispatchers.Default scope. Under runTest's virtual clock the boot timeouts would
            // elapse instantly while the real server bind is still in flight.
            withContext(Dispatchers.Default) {
                assertFailsWith<EngineLoadFailure> {
                    factory.createSession(
                        provider = RecordingPlaybackProvider(log),
                        webViewFactory = UnusedWebViewFactory(),
                        events = events(scope)
                    )
                }
            }

            assertTrue("engine.destroy" in log, "the engine must be torn down")
            assertTrue("tracker.destroy" in log, "the playback tracker must be detached from the provider")
            assertFalse(client!!.isActive, "the HTTP client must be closed")

            // The engine registers its teardown first, so it must unwind last.
            assertEquals(
                listOf("tracker.attach", "tracker.destroy", "engine.destroy"),
                log,
                "teardown must run in reverse registration order"
            )
        } finally {
            scope.cancel()
        }
    }
}
