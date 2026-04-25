package com.novage.p2pml

import com.novage.p2pml.api.events.P2PEventRegistry
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.api.models.toJsExpression
import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.engine.P2PEngineManager
import com.novage.p2pml.internal.server.ServerModule
import com.novage.p2pml.internal.server.config.LocalUrlFactory
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.utils.LogConfig
import com.novage.p2pml.internal.utils.RuntimeErrorDispatcher
import com.novage.p2pml.internal.webview.HeadlessWebView
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private enum class LoaderStatus { IDLE, INITIALIZING, ACTIVE, RELEASING, RELEASED }

internal class P2PMediaLoaderCore(
    private val coreConfig: CoreConfig = CoreConfig(),
    private val customEngineUrl: String? = null
) {
    companion object {
        private const val WEBVIEW_LOAD_TIMEOUT_MS = 15_000L

        fun enableLogging() {
            LogConfig.isEnabled = true
        }
        fun disableLogging() {
            LogConfig.isEnabled = false
        }
    }

    private val logger = CoreLogger("P2PMediaLoaderCore")
    private val urlFactory = LocalUrlFactory()
    internal var engineManager: P2PEngine? = null
    private val coreScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var serverModule: ServerModule? = null
    private var playbackProvider: PlaybackProvider? = null

    private val status = MutableStateFlow(LoaderStatus.IDLE)

    val events: P2PEventRegistry =
        P2PEventRegistry(coreScope, { engineManager }, { status.value == LoaderStatus.ACTIVE })

    private val errorDispatcher = RuntimeErrorDispatcher()

    val fatalErrors = errorDispatcher.errors

    private var pendingDynamicConfig: DynamicCoreConfig? = null

    @Suppress("ThrowsCount")
    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    internal suspend fun start(
        provider: PlaybackProvider,
        webViewFactory: (onLoaded: () -> Unit, onError: (P2PMediaLoaderErrorType, String) -> Unit) -> HeadlessWebView
    ) {
        withContext(Dispatchers.Main) {
            if (!status.compareAndSet(LoaderStatus.IDLE, LoaderStatus.INITIALIZING)) {
                val message = "Initialization skipped: Core is already in state ${status.value}"
                logger.w { message }
                throw P2PMediaLoaderException(P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR, message)
            }

            logger.d { "Initializing P2PMediaLoaderCore..." }
            this@P2PMediaLoaderCore.playbackProvider = provider

            monitorRuntimeErrors()

            try {
                performInitialization(provider, webViewFactory)
            } catch (e: P2PMediaLoaderException) {
                logger.e { "Initialization failed: ${e.message}" }
                release()
                throw e
            } catch (e: TimeoutCancellationException) {
                logger.e { "Initialization timed out waiting for WebView." }
                release()
                throw P2PMediaLoaderException(
                    P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR,
                    "WebView initialization timed out",
                    e
                )
            } catch (e: CancellationException) {
                logger.d { "Initialization cancelled by coroutine scope." }
                release()
                throw e
            } catch (e: IllegalStateException) {
                logger.e { "Initialization failed due to invalid state: ${e.message}" }
                release()
                throw P2PMediaLoaderException(
                    P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR,
                    e.message ?: "Invalid state",
                    e
                )
            } catch (e: IllegalArgumentException) {
                logger.e { "Initialization failed due to invalid argument: ${e.message}" }
                release()
                throw P2PMediaLoaderException(
                    P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR,
                    e.message ?: "Invalid argument",
                    e
                )
            }
        }
    }

    private suspend fun performInitialization(
        provider: PlaybackProvider,
        webViewFactory: (onLoaded: () -> Unit, onError: (P2PMediaLoaderErrorType, String) -> Unit) -> HeadlessWebView
    ) {
        val webViewLoadedDeferred = CompletableDeferred<Unit>()
        val webView = webViewFactory(
            { webViewLoadedDeferred.complete(Unit) },
            { errorType, message ->
                val error = P2PMediaLoaderException(errorType, message)
                webViewLoadedDeferred.completeExceptionally(error)
                errorDispatcher.tryEmit(errorType, message)
            }
        )

        val engine = P2PEngineManager(webView)
        this@P2PMediaLoaderCore.engineManager = engine

        val module = ServerModule(
            playbackProvider = provider,
            engineManager = engine,
            urlFactory = urlFactory,
            enableCors = customEngineUrl != null,
            errorDispatcher = errorDispatcher
        )
        this@P2PMediaLoaderCore.serverModule = module

        val port = withContext(Dispatchers.IO) { module.start() }
        urlFactory.setPort(port)

        val engineFileUrl = customEngineUrl ?: urlFactory.buildStaticPageUrl()
        logger.d { "Loading P2P Engine from: $engineFileUrl" }
        engine.loadUrl(engineFileUrl)

        withTimeout(WEBVIEW_LOAD_TIMEOUT_MS) {
            webViewLoadedDeferred.await()
        }

        logger.i { "WebView loaded. Initializing Core JS Engine." }
        engine.initCoreEngine(
            coreConfig = coreConfig.toJsExpression(),
            uploadUrl = urlFactory.buildUploadUrl()
        )

        status.value = LoaderStatus.ACTIVE
        events.syncEarlySubscriptions()

        pendingDynamicConfig?.let {
            logger.i { "Applying cached pending dynamic config..." }
            engine.applyDynamicConfig(it.toJsExpression())
            pendingDynamicConfig = null
        }
    }

    private fun monitorRuntimeErrors() {
        errorDispatcher.errors.onEach { exception ->
            logger.e { "Runtime System Error Caught: ${exception.type} - ${exception.message}" }
            if (exception.type == P2PMediaLoaderErrorType.ENGINE_RUNTIME_ERROR) {
                release()
            }
        }.launchIn(coreScope)
    }

    @Throws(P2PMediaLoaderException::class)
    fun getManifestUrl(manifestUrl: String): String {
        if (status.value != LoaderStatus.ACTIVE) {
            throw P2PMediaLoaderException(
                P2PMediaLoaderErrorType.CORE_NOT_INITIALIZED_ERROR,
                "P2PMediaLoader is not ready. Current state: ${status.value}"
            )
        }
        return urlFactory.buildManifestUrl(manifestUrl.encodeURLParameter())
    }

    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) {
        when (status.value) {
            LoaderStatus.IDLE, LoaderStatus.INITIALIZING -> pendingDynamicConfig = dynamicCoreConfig

            LoaderStatus.ACTIVE -> engineManager?.applyDynamicConfig(dynamicCoreConfig.toJsExpression())

            LoaderStatus.RELEASING, LoaderStatus.RELEASED -> logger.w {
                "Ignored dynamic config. Core state: ${status.value}."
            }
        }
    }

    fun release() {
        while (true) {
            val current = status.value
            if (current != LoaderStatus.ACTIVE && current != LoaderStatus.INITIALIZING) return
            if (status.compareAndSet(current, LoaderStatus.RELEASING)) break
        }

        logger.i { "Releasing P2PMediaLoaderCore resources..." }

        val engineToDestroy = engineManager
        engineManager = null
        val providerToReset = playbackProvider
        playbackProvider = null
        val serverToDestroy = serverModule
        serverModule = null

        pendingDynamicConfig = null
        urlFactory.setPort(-1)

        coreScope.launch {
            try {
                runCatching { serverToDestroy?.destroy() }
                runCatching { engineToDestroy?.destroy() }
                runCatching { providerToReset?.resetData() }

                status.value = LoaderStatus.RELEASED
                logger.d { "Release complete." }
            } finally {
                coreScope.cancel()
            }
        }
    }
}
