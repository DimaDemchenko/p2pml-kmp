package com.novage.p2pml.internal.session

import com.novage.p2pml.P2PMediaLoaderErrorType
import com.novage.p2pml.P2PMediaLoaderException
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.api.models.toJsExpression
import com.novage.p2pml.internal.engine.P2PEngineManager
import com.novage.p2pml.internal.http.createHttpClient
import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.providers.SequenceStateTracker
import com.novage.p2pml.internal.server.ServerModule
import com.novage.p2pml.internal.server.config.LocalUrlFactory
import com.novage.p2pml.internal.server.services.ManifestService
import com.novage.p2pml.internal.server.services.SegmentService
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.utils.RuntimeErrorDispatcher
import com.novage.p2pml.internal.webview.HeadlessWebView
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class P2PSession(
    val engineManager: P2PEngineManager,
    private val urlFactory: LocalUrlFactory,
    private val teardownAction: suspend () -> Unit
) {
    fun getManifestUrl(manifestUrl: String): String = urlFactory.buildManifestUrl(manifestUrl)

    fun applyDynamicConfig(config: DynamicCoreConfig) {
        engineManager.applyDynamicConfig(config.toJsExpression())
    }

    suspend fun destroy() {
        teardownAction()
    }
}

internal class P2PSessionFactory(
    private val coreConfig: CoreConfig,
    private val errorDispatcher: RuntimeErrorDispatcher,
    private val customEngineUrl: String?
) {
    companion object {
        private const val WEBVIEW_LOAD_TIMEOUT_MS = 15_000L
    }

    private val logger = CoreLogger("P2PSessionFactory")

    suspend fun createSession(
        provider: PlaybackProvider,
        webViewFactory: (onLoaded: () -> Unit, onError: (P2PMediaLoaderErrorType, String) -> Unit) -> HeadlessWebView
    ): P2PSession {
        val urlFactory = LocalUrlFactory()
        val webViewLoadedDeferred = CompletableDeferred<Unit>()

        val webView = webViewFactory(
            { webViewLoadedDeferred.complete(Unit) },
            { errorType, message ->
                errorDispatcher.tryEmit(errorType, message)
                webViewLoadedDeferred.completeExceptionally(P2PMediaLoaderException(errorType, message))
            }
        )

        val engine = P2PEngineManager(webView)
        val client = createHttpClient()
        val hlsManifestManager = HlsManifestManager(provider, urlFactory)
        val sequenceStateTracker = SequenceStateTracker(provider, engine, hlsManifestManager, errorDispatcher)

        val manifestService = ManifestService(hlsManifestManager, engine) {
            logger.d { "Resetting playback and parser state via ManifestService" }
            provider.resetData()
            hlsManifestManager.reset()
            sequenceStateTracker.reset()
        }
        val segmentService = SegmentService(engine, sequenceStateTracker)

        val serverModule = ServerModule(
            client = client,
            hlsManifestManager = hlsManifestManager,
            manifestService = manifestService,
            segmentService = segmentService,
            enableCors = customEngineUrl != null,
            errorDispatcher = errorDispatcher
        )

        return runCatching {
            startServerAndEngine(engine, serverModule, urlFactory, webViewLoadedDeferred)

            P2PSession(
                engineManager = engine,
                urlFactory = urlFactory,
                teardownAction = {
                    segmentService.reset()
                    sequenceStateTracker.reset()
                    manifestService.resetState()
                    sequenceStateTracker.destroy()

                    serverModule.destroy()
                    engine.destroy()
                    provider.resetData()
                    client.close()
                }
            )
        }.onFailure { e ->
            if (e !is Exception) throw e

            if (e is TimeoutCancellationException) {
                logger.e { "Session boot timed out waiting for WebView." }
            } else if (e !is CancellationException) {
                logger.e { "Session boot failed: ${e.message}" }
            }

            cleanupPartialResources(serverModule, engine, client)

            throw e
        }.getOrThrow()
    }

    private suspend fun startServerAndEngine(
        engine: P2PEngineManager,
        serverModule: ServerModule,
        urlFactory: LocalUrlFactory,
        webViewLoadedDeferred: CompletableDeferred<Unit>
    ) {
        val port = withContext(Dispatchers.IO) { serverModule.start() }
        urlFactory.setPort(port)

        val engineFileUrl = customEngineUrl ?: urlFactory.buildStaticPageUrl()
        engine.loadUrl(engineFileUrl)

        withTimeout(WEBVIEW_LOAD_TIMEOUT_MS) {
            webViewLoadedDeferred.await()
        }

        engine.initCoreEngine(
            coreConfig = coreConfig.toJsExpression(),
            uploadUrl = urlFactory.buildUploadUrl()
        )
    }

    private suspend fun destroySafely(action: suspend () -> Unit) {
        runCatching { action() }.onFailure {
            if (it is CancellationException) throw it

            logger.w { "Failed to clean up resource: ${it.message}" }
        }
    }

    private suspend fun cleanupPartialResources(
        serverModule: ServerModule,
        engine: P2PEngineManager,
        client: HttpClient
    ) {
        destroySafely { serverModule.destroy() }
        destroySafely { engine.destroy() }
        destroySafely { client.close() }
    }
}
