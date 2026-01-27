package com.novage.p2pml

import com.novage.p2pml.api.interfaces.Cancellable
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.ChunkDownloadedDetails
import com.novage.p2pml.api.models.ChunkUploadedDetails
import com.novage.p2pml.api.models.PeerDetails
import com.novage.p2pml.api.models.PeerErrorDetails
import com.novage.p2pml.api.models.SegmentAbortDetails
import com.novage.p2pml.api.models.SegmentErrorDetails
import com.novage.p2pml.api.models.SegmentLoadDetails
import com.novage.p2pml.api.models.SegmentStartDetails
import com.novage.p2pml.api.models.TrackerErrorDetails
import com.novage.p2pml.api.models.TrackerWarningDetails
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

private enum class LoaderStatus { IDLE, INITIALIZING, ACTIVE }

abstract class P2PMediaLoaderCore(
    private val onReady: () -> Unit,
    private val onError: (P2PMediaLoaderErrorType, String) -> Unit,
    private val coreConfig: String = "{}",
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

    private var serverModule: ServerModule? = null
    private var playbackProvider: PlaybackProvider? = null

    private val status = MutableStateFlow(LoaderStatus.IDLE)
    private var pendingDynamicConfig: String? = null

    internal fun initialize(provider: PlaybackProvider, webViewFactory: () -> HeadlessWebView) {
        if (!status.compareAndSet(LoaderStatus.IDLE, LoaderStatus.INITIALIZING)) {
            logger.w { "Initialization skipped: Core is already in state ${status.value}" }
            return
        }

        logger.d { "Initializing P2PMediaLoaderCore..." }
        this.playbackProvider = provider

        val webView = webViewFactory()
        val engine = P2PEngineManager(webView, provider)
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
                when (errorType) {
                    P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR -> failInitialization(errorType, message)
                    else -> onError(errorType, message)
                }
            },
            onServerStarted = { port ->
                logger.i { "Local P2P Server started on port: $port" }
                urlFactory.setPort(port)

                onServerReady()
            }
        )
        this.serverModule = module

        module.start()
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

    fun applyDynamicConfig(dynamicCoreConfig: String) {
        if (status.value != LoaderStatus.ACTIVE) {
            logger.d { "Core not ready (Current: ${status.value}). Caching dynamic config for later application." }
            pendingDynamicConfig = dynamicCoreConfig
            return
        }
        val engine = engineManager ?: return

        logger.d { "Applying dynamic config: $dynamicCoreConfig" }
        engine.applyDynamicConfig(dynamicCoreConfig)
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
            coreConfig = coreConfig,
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
            engine.applyDynamicConfig(it)
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
        status.value = LoaderStatus.IDLE

        logger.i { "Releasing P2PMediaLoaderCore resources..." }

        eventEmitter.removeAllListeners()

        serverModule?.destroy()
        serverModule = null

        engineManager?.destroy()
        engineManager = null

        runBlocking { playbackProvider?.resetData() }
        playbackProvider = null

        urlFactory.setPort(-1)

        logger.d { "Release complete." }
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
