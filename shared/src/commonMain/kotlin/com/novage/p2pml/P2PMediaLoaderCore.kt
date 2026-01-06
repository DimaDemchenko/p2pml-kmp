package com.novage.p2pml

import com.novage.p2pml.domain.interfaces.P2PEngine
import com.novage.p2pml.domain.interfaces.PlaybackProvider
import com.novage.p2pml.engine.P2PEngineManager
import com.novage.p2pml.events.EventEmitter
import com.novage.p2pml.server.ServerModule
import com.novage.p2pml.server.config.LocalUrlFactory
import com.novage.p2pml.utils.CoreLogger
import com.novage.p2pml.utils.LogConfig
import com.novage.p2pml.webview.HeadlessWebView
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.runBlocking

abstract class P2PMediaLoaderCore(
    private val onP2PReadyCallback: () -> Unit,
    private val onP2PReadyErrorCallback: (message: String) -> Unit,
    private val coreConfigJson: String = "{}",
    private val customEngineFileUrl: String? = null,
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
    protected val eventEmitter = EventEmitter()

    protected var engineManager: P2PEngine? = null
        private set
    private var serverModule: ServerModule? = null
    private var playbackProvider: PlaybackProvider? = null

    protected var isEngineReady = false
        private set

    protected fun initialize(
        webView: HeadlessWebView,
        provider: PlaybackProvider,
    ) {
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
            enableCors = customEngineFileUrl != null,
            onServerStarted = { port ->
                logger.i { "Local P2P Server started on port: $port" }
                urlFactory.setPort(port)

                onServerReady()
            },
        )
        this.serverModule = module

        try {
            module.start()
        } catch (e: Exception) {
            failInitialization("Failed to start local server: ${e.message}")
        }
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

        val engineFileUrl = customEngineFileUrl ?: urlFactory.buildStaticPageUrl()
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
            uploadUrl = urlFactory.buildUploadUrl(),
        )

        val subscribedEvents = eventEmitter.getSubscribedEventNames()
        if (subscribedEvents.isNotEmpty()) {
            logger.d { "Subscribing to events: $subscribedEvents" }
            subscribedEvents.forEach { eventName ->
                engine.subscribeToP2PEvent(eventName)
            }
        }

        isEngineReady = true
        onP2PReadyCallback()
    }

    protected fun failInitialization(message: String) {
        logger.e { "Initialization failed: $message" }
        onP2PReadyErrorCallback(message)
        release()
    }

    open fun release() {
        logger.i { "Releasing P2PMediaLoaderCore resources..." }

        eventEmitter.removeAllListeners()

        engineManager?.destroy()
        engineManager = null

        serverModule?.destroy()
        serverModule = null

        runBlocking { playbackProvider?.resetData() }
        isEngineReady = false

        urlFactory.setPort(-1)

        logger.d { "Release complete." }
    }
}
