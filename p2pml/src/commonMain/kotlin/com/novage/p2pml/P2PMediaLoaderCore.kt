package com.novage.p2pml

import com.novage.p2pml.api.interfaces.Cancellable
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.ChunkDownloadedDetails
import com.novage.p2pml.api.models.ChunkUploadedDetails
import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.api.models.PeerDetails
import com.novage.p2pml.api.models.PeerErrorDetails
import com.novage.p2pml.api.models.SegmentAbortDetails
import com.novage.p2pml.api.models.SegmentErrorDetails
import com.novage.p2pml.api.models.SegmentLoadDetails
import com.novage.p2pml.api.models.SegmentStartDetails
import com.novage.p2pml.api.models.TrackerErrorDetails
import com.novage.p2pml.api.models.TrackerWarningDetails
import com.novage.p2pml.api.models.toJsExpression
import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.engine.P2PEngineManager
import com.novage.p2pml.internal.events.CoreEventEmitter
import com.novage.p2pml.internal.events.CoreEventMap
import com.novage.p2pml.internal.events.EventEmitter
import com.novage.p2pml.internal.events.EventListener
import com.novage.p2pml.internal.server.ServerModule
import com.novage.p2pml.internal.server.config.LocalUrlFactory
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.utils.LogConfig
import com.novage.p2pml.internal.webview.HeadlessWebView
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class LoaderStatus { IDLE, INITIALIZING, ACTIVE, RELEASING }

abstract class P2PMediaLoaderCore(
    private val onReady: () -> Unit,
    private val onError: (P2PMediaLoaderErrorType, String) -> Unit,
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
    internal val eventEmitter: CoreEventEmitter = EventEmitter()
    internal var engineManager: P2PEngine? = null
    private val coreScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var serverModule: ServerModule? = null
    private var playbackProvider: PlaybackProvider? = null

    private var startJob: Job? = null

    private val status = MutableStateFlow(LoaderStatus.IDLE)
    private var pendingDynamicConfig: DynamicCoreConfig? = null

    internal fun initialize(provider: PlaybackProvider, webViewFactory: () -> HeadlessWebView) {
        if (!status.compareAndSet(LoaderStatus.IDLE, LoaderStatus.INITIALIZING)) {
            logger.w { "Initialization skipped: Core is already in state ${status.value}" }
            return
        }

        logger.d { "Initializing P2PMediaLoaderCore..." }
        this.playbackProvider = provider

        val webView = webViewFactory()
        val engine = P2PEngineManager(webView)
        this.engineManager = engine

        startLocalServer(provider, engine)
    }

    private fun startLocalServer(provider: PlaybackProvider, engine: P2PEngine) {
        val module = ServerModule(
            playbackProvider = provider,
            engineManager = engine,
            urlFactory = urlFactory,
            enableCors = customEngineUrl != null,
            onError = { errorType, message ->
                if (status.value != LoaderStatus.RELEASING && status.value != LoaderStatus.IDLE) {
                    when (errorType) {
                        P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR -> failInitialization(errorType, message)
                        else -> onError(errorType, message)
                    }
                }
            },
            onServerStarted = { port ->
                if (status.value != LoaderStatus.RELEASING && status.value != LoaderStatus.IDLE) {
                    logger.i { "Local P2P Server started on port: $port" }
                    urlFactory.setPort(port)
                    onServerReady()
                } else {
                    logger.w { "Server started on port $port but core is ${status.value}, ignoring port." }
                }
            }
        )
        this.serverModule = module

        startJob = coreScope.launch {
            if (!isActive) {
                logger.d { "Start job cancelled before execution. Aborting server start." }
                return@launch
            }
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

            LoaderStatus.RELEASING -> {
                logger.w { "Ignored dynamic config request. Core is currently releasing." }
            }
        }
    }

    private fun onServerReady() {
        val engine = engineManager ?: return

        val engineFileUrl = customEngineUrl ?: urlFactory.buildStaticPageUrl()
        logger.d { "Loading P2P Engine from: $engineFileUrl" }
        engine.loadUrl(engineFileUrl)
    }

    protected fun onWebViewLoaded() {
        val engine = engineManager ?: run {
            logger.w { "WebView loaded but engineManager is null." }
            return
        }

        logger.i { "WebView loaded. Initializing Core JS Engine." }

        engine.initCoreEngine(
            coreConfig = coreConfig.toJsExpression(),
            uploadUrl = urlFactory.buildUploadUrl()
        )

        val subscribedEvents = eventEmitter.getSubscribedEventNames()
        if (subscribedEvents.isNotEmpty()) {
            logger.d { "Subscribing to events: $subscribedEvents" }
            subscribedEvents.forEach { eventName ->
                engine.subscribeToP2PEvent(eventName)
            }
        }

        status.value = LoaderStatus.ACTIVE

        pendingDynamicConfig?.let {
            logger.i { "Applying cached pending dynamic config..." }
            engine.applyDynamicConfig(it.toJsExpression())
            pendingDynamicConfig = null
        }

        onReady()
    }

    protected fun failInitialization(errorType: P2PMediaLoaderErrorType, message: String) {
        logger.e { "Initialization failed: $message" }
        onError(errorType, message)
        release()
    }

    open fun release() {
        val isReleasingFromActive = status.compareAndSet(LoaderStatus.ACTIVE, LoaderStatus.RELEASING)
        val isReleasingFromInit = status.compareAndSet(LoaderStatus.INITIALIZING, LoaderStatus.RELEASING)

        if (!isReleasingFromActive && !isReleasingFromInit) {
            logger.d { "Release ignored. Core is already ${status.value}." }
            return
        }

        logger.i { "Releasing P2PMediaLoaderCore resources..." }

        eventEmitter.removeAllListeners()

        val jobToCancel = startJob
        startJob = null
        jobToCancel?.cancel()

        val serverToDestroy = serverModule
        serverModule = null

        val engineToDestroy = engineManager
        engineManager = null

        val providerToReset = playbackProvider
        playbackProvider = null

        pendingDynamicConfig = null

        urlFactory.setPort(-1)

        coreScope.launch {
            jobToCancel?.join()

            runCatching { serverToDestroy?.destroy() }
                .onFailure { logger.e(it) { "Error destroying server module: ${it.message}" } }

            runCatching { engineToDestroy?.destroy() }
                .onFailure { logger.e(it) { "Error destroying P2P engine: ${it.message}" } }

            runCatching { providerToReset?.resetData() }
                .onFailure { logger.e(it) { "Error resetting playback provider: ${it.message}" } }

            status.value = LoaderStatus.IDLE
            logger.d { "Release complete." }
        }
    }

    private fun <T> registerListener(event: CoreEventMap<T>, block: (T) -> Unit): Cancellable {
        val listener = EventListener<T> { block(it) }
        val isFirstListener = !eventEmitter.hasListeners(event)

        eventEmitter.addEventListener(event, listener)

        if (status.value == LoaderStatus.ACTIVE && isFirstListener) {
            engineManager?.subscribeToP2PEvent(event.eventName)
        }

        return object : Cancellable {
            override fun cancel() {
                eventEmitter.removeEventListener(event, listener)

                val isNowEmpty = !eventEmitter.hasListeners(event)
                if (status.value == LoaderStatus.ACTIVE && isNowEmpty) {
                    engineManager?.unsubscribeFromP2PEvent(event.eventName)
                }
            }
        }
    }

    fun onSegmentLoaded(block: (SegmentLoadDetails) -> Unit) = registerListener(CoreEventMap.OnSegmentLoaded, block)
    fun onSegmentStart(block: (SegmentStartDetails) -> Unit) = registerListener(CoreEventMap.OnSegmentStart, block)
    fun onSegmentError(block: (SegmentErrorDetails) -> Unit) = registerListener(CoreEventMap.OnSegmentError, block)
    fun onSegmentAbort(block: (SegmentAbortDetails) -> Unit) = registerListener(CoreEventMap.OnSegmentAbort, block)

    fun onPeerConnect(block: (PeerDetails) -> Unit) = registerListener(CoreEventMap.OnPeerConnect, block)
    fun onPeerClose(block: (PeerDetails) -> Unit) = registerListener(CoreEventMap.OnPeerClose, block)
    fun onPeerError(block: (PeerErrorDetails) -> Unit) = registerListener(CoreEventMap.OnPeerError, block)

    fun onChunkDownloaded(block: (ChunkDownloadedDetails) -> Unit) =
        registerListener(CoreEventMap.OnChunkDownloaded, block)

    fun onChunkUploaded(block: (ChunkUploadedDetails) -> Unit) = registerListener(CoreEventMap.OnChunkUploaded, block)

    fun onTrackerError(block: (TrackerErrorDetails) -> Unit) = registerListener(CoreEventMap.OnTrackerError, block)
    fun onTrackerWarning(block: (TrackerWarningDetails) -> Unit) =
        registerListener(CoreEventMap.OnTrackerWarning, block)
}
