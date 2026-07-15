package com.novage.p2pml.internal.session

import com.novage.p2pml.api.config.CoreConfig
import com.novage.p2pml.api.errors.P2PMediaLoaderErrorCode
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.api.logging.P2PLogging
import com.novage.p2pml.api.playback.PlaybackProvider
import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.engine.P2PEngineManager
import com.novage.p2pml.internal.http.createHttpClient
import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.playback.SequenceStateTracker
import com.novage.p2pml.internal.server.ServerModule
import com.novage.p2pml.internal.server.config.LocalUrlFactory
import com.novage.p2pml.internal.server.services.ManifestService
import com.novage.p2pml.internal.server.services.SegmentService
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.webview.WebViewFactory
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class P2PSessionFactory(
    private val coreConfig: CoreConfig,
    private val onFatalError: (P2PMediaLoaderException) -> Unit,
    private val customEngineUrl: String?,
    private val httpClientProvider: () -> HttpClient = ::createHttpClient,
    private val engineProvider: suspend (
        WebViewFactory,
        P2PEvents
    ) -> P2PEngine = { webViewFactory, events ->
        withContext(Dispatchers.Main + NonCancellable) {
            val webView = webViewFactory.createHeadlessWebView(events) { exception ->
                onFatalError(exception)
            }
            P2PEngineManager(webView)
        }
    }
) {
    companion object {
        private const val WEBVIEW_LOAD_TIMEOUT_MS = 15_000L

        /**
         * A healthy engine acks near-instantly once the page is loaded; the budget is generous
         * because both legs of the ack round-trip transit the main thread, which can be starved
         * for seconds during app cold start on low-end devices.
         */
        private const val CORE_INIT_ACK_TIMEOUT_MS = 10_000L

        /** `debug` library namespaces enabled in the engine page when debug logging is on. */
        private const val ENGINE_DEBUG_NAMESPACES = "p2pml-core:*"
    }

    private val logger = CoreLogger("P2PSessionFactory")

    suspend fun createSession(
        provider: PlaybackProvider,
        webViewFactory: WebViewFactory,
        events: P2PEvents
    ): P2PSession {
        val cleanupTasks = mutableListOf<suspend () -> Unit>()

        return runCatching {
            val urlFactory = LocalUrlFactory()

            val engine = engineProvider(webViewFactory, events)
            cleanupTasks.add { engine.destroy() }
            currentCoroutineContext().ensureActive()

            val client = httpClientProvider()
            cleanupTasks.add { client.close() }

            val hlsManifestManager = HlsManifestManager(urlFactory)

            val sequenceStateTracker = SequenceStateTracker(provider, engine, hlsManifestManager)
            cleanupTasks.add { sequenceStateTracker.destroy() }

            val manifestService = ManifestService(hlsManifestManager, engine) {
                logger.d { "Resetting playback and parser state via ManifestService" }
                hlsManifestManager.reset()
                sequenceStateTracker.reset()
            }
            cleanupTasks.add { manifestService.resetState() }

            val segmentService = SegmentService(engine, sequenceStateTracker)
            cleanupTasks.add { segmentService.reset() }

            val serverModule = ServerModule(
                client = client,
                hlsManifestManager = hlsManifestManager,
                manifestService = manifestService,
                segmentService = segmentService,
                sessionToken = urlFactory.sessionToken,
                enableCors = customEngineUrl != null
            )
            cleanupTasks.add { serverModule.destroy() }

            val performFullTeardown: suspend () -> Unit = {
                cleanupSafely(cleanupTasks.reversed())
            }

            startServerAndEngine(engine, serverModule, urlFactory)

            P2PSession(
                engine = engine,
                urlFactory = urlFactory,
                teardownAction = performFullTeardown
            )
        }.onFailure { e ->
            handleInitializationFailure(e, cleanupTasks)
        }.getOrThrow()
    }

    private suspend fun startServerAndEngine(
        engine: P2PEngine,
        serverModule: ServerModule,
        urlFactory: LocalUrlFactory
    ) {
        val port = withContext(Dispatchers.IO) { serverModule.start() }
        urlFactory.setPort(port)

        val engineFileUrl = customEngineUrl ?: buildEnginePageUrl(urlFactory)
        withStartupTimeout(
            timeoutMs = WEBVIEW_LOAD_TIMEOUT_MS,
            code = P2PMediaLoaderErrorCode.ENGINE_LOAD_TIMEOUT,
            message = "Engine page did not load within ${WEBVIEW_LOAD_TIMEOUT_MS}ms."
        ) {
            engine.loadUrlAndWait(engineFileUrl)
        }

        withStartupTimeout(
            timeoutMs = CORE_INIT_ACK_TIMEOUT_MS,
            code = P2PMediaLoaderErrorCode.ENGINE_INIT_FAILED,
            message = "JS core did not acknowledge initialization within ${CORE_INIT_ACK_TIMEOUT_MS}ms. " +
                "Likely causes: the engine page does not implement the init ack " +
                "(outdated custom page), or the init script failed to evaluate " +
                "(e.g. invalid custom JS snippets in CoreConfig)."
        ) {
            engine.initCoreEngineAndWait(
                coreConfig = coreConfig,
                uploadUrl = urlFactory.buildUploadUrl()
            )
        }
    }

    /**
     * Maps this step's own timeout to a typed [P2PMediaLoaderException] at the site, so boot
     * timeouts cannot be mislabeled by generic mapping layers. If the enclosing scope is being
     * cancelled (including an outer caller's withTimeout), rethrows that cancellation instead of
     * converting it into a fake engine failure.
     */
    private suspend fun withStartupTimeout(
        timeoutMs: Long,
        code: P2PMediaLoaderErrorCode,
        message: String,
        block: suspend () -> Unit
    ) = try {
        withTimeout(timeoutMs) { block() }
    } catch (e: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        throw P2PMediaLoaderException(code, message, cause = e)
    }

    /**
     * The `debug` flag makes the engine page enable the bundled `debug` library (via
     * localStorage, before the module evaluates) and forward verbose console output
     * to the native log bridge. Without it only warnings, errors and uncaught
     * exceptions are forwarded.
     */
    private fun buildEnginePageUrl(urlFactory: LocalUrlFactory): String {
        val base = urlFactory.buildStaticPageUrl()
        return if (P2PLogging.isDebugEnabled) {
            "$base?debug=${ENGINE_DEBUG_NAMESPACES.encodeURLParameter()}"
        } else {
            base
        }
    }

    private suspend fun cleanupSafely(actions: Iterable<suspend () -> Unit>) = withContext(NonCancellable) {
        for (action in actions) {
            runCatching { action() }.onFailure {
                logger.w(it) { "Failed to clean up resource" }
            }
        }
    }

    private suspend fun handleInitializationFailure(e: Throwable, cleanupTasks: List<suspend () -> Unit>) {
        if (e !is CancellationException) {
            logger.e(e) { "Session boot failed" }
        }

        cleanupSafely(cleanupTasks.reversed())
        throw e
    }
}
