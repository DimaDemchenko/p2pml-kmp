package com.novage.p2pml

import com.novage.p2pml.domain.interfaces.Cancellable
import com.novage.p2pml.domain.interfaces.CoreEventEmitter
import com.novage.p2pml.domain.interfaces.EventListener
import com.novage.p2pml.domain.interfaces.HeadlessWebView
import com.novage.p2pml.domain.interfaces.P2PEngine
import com.novage.p2pml.domain.interfaces.PlaybackProvider
import com.novage.p2pml.domain.models.ChunkDownloadedDetails
import com.novage.p2pml.domain.models.ChunkUploadedDetails
import com.novage.p2pml.domain.models.CoreEventMap
import com.novage.p2pml.domain.models.PeerDetails
import com.novage.p2pml.domain.models.PeerErrorDetails
import com.novage.p2pml.domain.models.SegmentAbortDetails
import com.novage.p2pml.domain.models.SegmentErrorDetails
import com.novage.p2pml.domain.models.SegmentLoadDetails
import com.novage.p2pml.domain.models.SegmentStartDetails
import com.novage.p2pml.domain.models.TrackerErrorDetails
import com.novage.p2pml.domain.models.TrackerWarningDetails
import com.novage.p2pml.engine.P2PEngineManager
import com.novage.p2pml.events.EventEmitter
import com.novage.p2pml.server.ServerModule
import com.novage.p2pml.server.config.LocalUrlFactory
import com.novage.p2pml.utils.CoreLogger
import com.novage.p2pml.utils.LogConfig
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.runBlocking

abstract class P2PMediaLoaderCore(
    private val onReady: () -> Unit,
    private val onError: (message: String) -> Unit,
    private val coreConfigJson: String = "{}",
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
    protected val eventEmitter: CoreEventEmitter = EventEmitter()

    protected var engineManager: P2PEngine? = null
        private set
    private var serverModule: ServerModule? = null
    private var playbackProvider: PlaybackProvider? = null

    protected var isEngineReady = false
        private set

    protected fun initialize(webView: HeadlessWebView, provider: PlaybackProvider) {
        if (engineManager != null) {
            logger.w { "Initialize called but engine is already created. Ignoring." }
            return
        }

        logger.d { "Initializing P2PMediaLoaderCore..." }
        this.playbackProvider = provider

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
            onServerStartError = ::failInitialization,
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
        logger.d { "Building manifest URL for: $manifestUrl" }
        return urlFactory.buildManifestUrl(manifestUrl.encodeURLParameter())
    }

    fun applyDynamicConfig(dynamicCoreConfigJson: String) {
        val engine = engineManager ?: run {
            logger.w { "Cannot apply dynamic config: engineManager is null." }
            return
        }

        logger.d { "Applying dynamic config: $dynamicCoreConfigJson" }
        engine.applyDynamicConfig(dynamicCoreConfigJson)
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
            coreConfigJson = coreConfigJson,
            uploadUrl = urlFactory.buildUploadUrl()
        )

        val subscribedEvents = eventEmitter.getSubscribedEventNames()
        if (subscribedEvents.isNotEmpty()) {
            logger.d { "Subscribing to events: $subscribedEvents" }
            subscribedEvents.forEach { eventName ->
                engine.subscribeToP2PEvent(eventName)
            }
        }

        isEngineReady = true
        onReady()
    }

    protected fun failInitialization(message: String) {
        logger.e { "Initialization failed: $message" }
        onError(message)
        release()
    }

    open fun release() {
        logger.i { "Releasing P2PMediaLoaderCore resources..." }

        isEngineReady = false

        eventEmitter.removeAllListeners()

        serverModule?.destroy()
        serverModule = null

        engineManager?.destroy()
        engineManager = null

        runBlocking { playbackProvider?.resetData() }
        urlFactory.setPort(-1)

        logger.d { "Release complete." }
    }

    private fun <T> registerListener(event: CoreEventMap<T>, block: (T) -> Unit): Cancellable {
        val listener = EventListener<T> { block(it) }
        val isFirstListener = !eventEmitter.hasListeners(event)

        eventEmitter.addEventListener(event, listener)

        if (isEngineReady && isFirstListener) {
            engineManager?.subscribeToP2PEvent(event.eventName)
        }

        return object : Cancellable {
            override fun cancel() {
                eventEmitter.removeEventListener(event, listener)

                val isNowEmpty = !eventEmitter.hasListeners(event)
                if (isEngineReady && isNowEmpty) {
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
