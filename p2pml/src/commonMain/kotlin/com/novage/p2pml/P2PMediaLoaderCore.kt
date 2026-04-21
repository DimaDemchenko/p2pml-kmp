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
import com.novage.p2pml.internal.webview.HeadlessWebView
import io.ktor.http.encodeURLParameter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private enum class LoaderStatus { IDLE, INITIALIZING, ACTIVE, RELEASING, RELEASED }

abstract class P2PMediaLoaderCore(
    private val coreConfig: CoreConfig = CoreConfig(),
    private val customEngineUrl: String? = null
) {
    companion object {
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

    private var startJob: Job? = null

    private val status = MutableStateFlow(LoaderStatus.IDLE)
    val events: P2PEventRegistry =
        P2PEventRegistry(coreScope, { engineManager }, { status.value == LoaderStatus.ACTIVE })
    private var pendingDynamicConfig: DynamicCoreConfig? = null

    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    internal suspend fun start(
        provider: PlaybackProvider,
        webViewFactory: (onLoaded: () -> Unit, onError: (P2PMediaLoaderErrorType, String) -> Unit) -> HeadlessWebView
    ) {
        if (!status.compareAndSet(LoaderStatus.IDLE, LoaderStatus.INITIALIZING)) {
            val message = "Initialization skipped: Core is already in state ${status.value}"
            logger.w { message }
            throw IllegalStateException(message)
        }

        logger.d { "Initializing P2PMediaLoaderCore..." }
        this.playbackProvider = provider

        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                logger.w { "Core initialization cancelled. Releasing resources." }
                release()
            }

            val onLoaded = {
                val engine = engineManager
                if (engine == null) {
                    val msg = "WebView loaded but engineManager is null."
                    logger.w { msg }
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(msg))
                } else {
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
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }

            val onError = { errorType: P2PMediaLoaderErrorType, message: String ->
                logger.e { "Initialization failed: $message" }
                release()
                if (continuation.isActive) continuation.resumeWithException(P2PMediaLoaderException(errorType, message))
            }

            val webView = webViewFactory(onLoaded, onError)
            val engine = P2PEngineManager(webView)
            this.engineManager = engine

            startLocalServer(provider, engine, onError)
        }
    }

    private fun startLocalServer(
        provider: PlaybackProvider,
        engine: P2PEngine,
        onStartupError: (P2PMediaLoaderErrorType, String) -> Unit
    ) {
        startJob = coreScope.launch {
            if (!isActive) {
                logger.d { "Start job cancelled before execution. Aborting server start." }
                return@launch
            }

            val module = ServerModule(
                playbackProvider = provider,
                engineManager = engine,
                urlFactory = urlFactory,
                enableCors = customEngineUrl != null,
                onError = { errorType, message ->
                    if (status.value == LoaderStatus.INITIALIZING || status.value == LoaderStatus.ACTIVE) {
                        when (errorType) {
                            P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR -> onStartupError(errorType, message)
                            else -> logger.e { "Server module runtime error: $errorType - $message" }
                        }
                    }
                },
                onServerStarted = { port ->
                    if (status.value == LoaderStatus.INITIALIZING || status.value == LoaderStatus.ACTIVE) {
                        logger.i { "Local P2P Server started on port: $port" }
                        urlFactory.setPort(port)
                        onServerReady()
                    } else {
                        logger.w { "Server started on port $port but core is ${status.value}, ignoring port." }
                    }
                }
            )

            if (!isActive) {
                withContext(NonCancellable) {
                    runCatching { module.destroy() }
                        .onFailure { logger.e(it) { "Error explicitly destroying module on cancel: ${it.message}" } }
                }
                return@launch
            }

            this@P2PMediaLoaderCore.serverModule = module
            module.start()
        }
    }

    fun getManifestUrl(manifestUrl: String): String {
        if (status.value != LoaderStatus.ACTIVE) {
            logger.e {
                "Attempted to build manifest URL but Core is not ACTIVE " +
                    "(Current: ${status.value}). Returning original URL."
            }
            return manifestUrl
        }

        logger.d { "Building manifest URL for: $manifestUrl" }
        return urlFactory.buildManifestUrl(manifestUrl.encodeURLParameter())
    }

    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) {
        when (status.value) {
            LoaderStatus.IDLE, LoaderStatus.INITIALIZING -> {
                logger.d { "Core not ready. Caching dynamic config for later application." }
                pendingDynamicConfig = dynamicCoreConfig
            }

            LoaderStatus.ACTIVE -> {
                logger.d { "Applying dynamic config..." }
                engineManager?.applyDynamicConfig(dynamicCoreConfig.toJsExpression())
            }

            LoaderStatus.RELEASING, LoaderStatus.RELEASED -> {
                logger.w { "Ignored dynamic config request. Core is currently in state ${status.value}." }
            }
        }
    }

    private fun onServerReady() {
        val engine = engineManager ?: return

        val engineFileUrl = customEngineUrl ?: urlFactory.buildStaticPageUrl()
        logger.d { "Loading P2P Engine from: $engineFileUrl" }
        engine.loadUrl(engineFileUrl)
    }

    open fun release() {
        while (true) {
            val current = status.value
            if (current != LoaderStatus.ACTIVE && current != LoaderStatus.INITIALIZING) {
                logger.d { "Release ignored. Core is already ${status.value}." }
                return
            }
            if (status.compareAndSet(current, LoaderStatus.RELEASING)) {
                break
            }
        }

        logger.i { "Releasing P2PMediaLoaderCore resources..." }

        val jobToCancel = startJob
        startJob = null
        jobToCancel?.cancel()

        val engineToDestroy = engineManager
        engineManager = null

        val providerToReset = playbackProvider
        playbackProvider = null

        pendingDynamicConfig = null
        urlFactory.setPort(-1)

        coreScope.launch {
            try {
                jobToCancel?.join()

                val serverToDestroy = serverModule
                serverModule = null

                runCatching { serverToDestroy?.destroy() }
                    .onFailure { logger.e(it) { "Error destroying server module: ${it.message}" } }

                runCatching { engineToDestroy?.destroy() }
                    .onFailure { logger.e(it) { "Error destroying P2P engine: ${it.message}" } }

                runCatching { providerToReset?.resetData() }
                    .onFailure { logger.e(it) { "Error resetting playback provider: ${it.message}" } }

                status.value = LoaderStatus.RELEASED
                logger.d { "Release complete." }
            } finally {
                coreScope.cancel()
            }
        }
    }
}
