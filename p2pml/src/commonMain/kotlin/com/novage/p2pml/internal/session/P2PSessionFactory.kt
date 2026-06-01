package com.novage.p2pml.internal.session

import com.novage.p2pml.api.events.P2PEventRegistry
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.CoreConfig
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
import com.novage.p2pml.internal.utils.RuntimeErrorDispatcher
import com.novage.p2pml.internal.webview.WebViewFactory
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class P2PSessionFactory(
    private val coreConfig: CoreConfig,
    private val errorDispatcher: RuntimeErrorDispatcher,
    private val customEngineUrl: String?,
    private val httpClientProvider: () -> HttpClient = ::createHttpClient,
    private val engineProvider: suspend (
        WebViewFactory,
        P2PEventRegistry
    ) -> P2PEngine = { webViewFactory, events ->
        withContext(Dispatchers.Main) {
            val webView = webViewFactory.createHeadlessWebView(events) { exception ->
                errorDispatcher.tryEmit(exception.type, exception.message ?: "Unknown error")
            }
            P2PEngineManager(webView)
        }
    }
) {
    companion object {
        private const val WEBVIEW_LOAD_TIMEOUT_MS = 15_000L
    }

    private val logger = CoreLogger("P2PSessionFactory")

    suspend fun createSession(
        provider: PlaybackProvider,
        webViewFactory: WebViewFactory,
        events: P2PEventRegistry
    ): P2PSession {
        val cleanupTasks = mutableListOf<suspend () -> Unit>()

        return runCatching {
            val urlFactory = LocalUrlFactory()

            val engine = engineProvider(webViewFactory, events)
            cleanupTasks.add { engine.destroy() }

            val client = httpClientProvider()
            cleanupTasks.add { client.close() }

            val hlsManifestManager = HlsManifestManager(urlFactory)

            val sequenceStateTracker = SequenceStateTracker(provider, engine, hlsManifestManager, errorDispatcher)
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
                enableCors = customEngineUrl != null,
                errorDispatcher = errorDispatcher
            )
            cleanupTasks.add { serverModule.destroy() }

            val performFullTeardown: suspend () -> Unit = {
                cleanupSafely(cleanupTasks.reversed())
            }

            startServerAndEngine(engine, serverModule, urlFactory)

            P2PSession(
                engineManager = engine,
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

        val engineFileUrl = customEngineUrl ?: urlFactory.buildStaticPageUrl()
        withTimeout(WEBVIEW_LOAD_TIMEOUT_MS) {
            engine.loadUrlAndWait(engineFileUrl)
        }

        engine.initCoreEngine(
            coreConfig = coreConfig,
            uploadUrl = urlFactory.buildUploadUrl()
        )
    }

    private suspend fun cleanupSafely(actions: Iterable<suspend () -> Unit>) = withContext(NonCancellable) {
        for (action in actions) {
            runCatching { action() }.onFailure {
                logger.w { "Failed to clean up resource: $it" }
            }
        }
    }

    private suspend fun handleInitializationFailure(e: Throwable, cleanupTasks: List<suspend () -> Unit>) {
        if (e !is Exception) throw e

        if (e is TimeoutCancellationException) {
            logger.e { "Session boot timed out waiting for WebView." }
        } else if (e !is CancellationException) {
            logger.e { "Session boot failed: ${e.message}" }
        }

        cleanupSafely(cleanupTasks.reversed())
        throw e
    }
}
