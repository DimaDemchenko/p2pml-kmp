package com.novage.p2pml

import com.novage.p2pml.domain.interfaces.P2PEngine
import com.novage.p2pml.engine.P2PEngineManager
import com.novage.p2pml.events.*
import com.novage.p2pml.domain.interfaces.PlaybackProvider
import com.novage.p2pml.server.config.LocalUrlFactory
import com.novage.p2pml.server.config.ServerConfig
import com.novage.p2pml.server.ServerModule
import com.novage.p2pml.utils.CoreLogger
import com.novage.p2pml.utils.LogConfig
import com.novage.p2pml.webview.HeadlessWebView
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.runBlocking

abstract class P2PMediaLoaderCore(
    private val onP2PReadyCallback: () -> Unit,
    private val onP2PReadyErrorCallback: (message: String) -> Unit,
    private val coreConfigJson : String = "{}",
    private val customEngineFileUrl: String? = null
) {
    private val logger = CoreLogger("P2PMediaLoaderCore")

    companion object {
        fun enableLogging() {
            LogConfig.isEnabled = true
        }

        fun disableLogging() {
            LogConfig.isEnabled = false
        }
    }

    private val serverConfig = ServerConfig()
    protected val urlFactory = LocalUrlFactory(serverConfig)
    protected val eventEmitter = EventEmitter()

    protected var engineManager: P2PEngine? = null
    private var serverModule: ServerModule? = null
    private var playbackProvider: PlaybackProvider? = null

    protected var isEngineReady = false

    protected fun initialize(
        webView: HeadlessWebView,
        provider: PlaybackProvider
    ) {
        if (engineManager != null) {
            logger.w { "Initialize called but engine is already created. Ignoring." }
            return
        }

        logger.d { "Initializing P2PMediaLoaderCore..." }

        this.playbackProvider = provider

        val engine = P2PEngineManager(webView, provider)
        this.engineManager = engine

        val module = ServerModule(
            playbackProvider = provider,
            engineManager = engine,
            urlFactory = urlFactory,
            onServerStarted = { port ->
                logger.i { "Local P2P Server started on port: $port" }
                serverConfig.updatePort(port)
                onServerStarted()
            }
        )
        this.serverModule = module

        try {
            module.start()
        } catch (e: Exception) {
            logger.e(e) { "P2P Server failed to start" }
            onP2PReadyErrorCallback(e.message ?: "P2P Server failed to start")
        }
    }

    fun getManifestUrl(manifestUrl: String): String {
        logger.d { "Building manifest URL for: $manifestUrl" }
        return urlFactory.buildManifestUrl(manifestUrl.encodeURLParameter())
    }

    fun applyDynamicConfig(dynamicCoreConfigJson: String) {
        logger.d { "Applying dynamic config: $dynamicCoreConfigJson" }
        engineManager?.applyDynamicConfig(dynamicCoreConfigJson)
    }

    open fun release() {
        logger.i { "Releasing P2PMediaLoaderCore resources..." }

        eventEmitter.removeAllListeners()

        engineManager?.destroy()
        engineManager = null

        serverModule?.stop()
        serverModule = null

        runBlocking { playbackProvider?.resetData() }
        isEngineReady = false
        serverConfig.updatePort(-1)

        logger.d { "Release complete." }
    }

    private fun onServerStarted() {
        val engineFileUrl = customEngineFileUrl ?: urlFactory.buildStaticPageUrl()
        logger.d { "Loading P2P Engine from: $engineFileUrl" }
        engineManager?.loadUrl(engineFileUrl)
    }

    protected fun onWebViewLoaded() {
        logger.i { "WebView loaded. Initializing Core JS Engine." }

        engineManager?.initCoreEngine(
            coreConfigJson = coreConfigJson,
            uploadUrl = urlFactory.buildUploadUrl()
        )

        val subscribedEvents = eventEmitter.getSubscribedEventNames()
        if (subscribedEvents.isNotEmpty()) {
            logger.d { "Subscribing to events: $subscribedEvents" }
        }

        subscribedEvents.forEach { eventName ->
            engineManager?.subscribeToP2PEvent(eventName)
        }

        isEngineReady = true
        onP2PReadyCallback()
    }
}
