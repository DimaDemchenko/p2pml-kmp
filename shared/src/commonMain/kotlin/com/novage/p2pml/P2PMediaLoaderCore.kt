package com.novage.p2pml

import com.novage.p2pml.domain.interfaces.P2PEngine
import com.novage.p2pml.engine.P2PEngineManager
import com.novage.p2pml.events.*
import com.novage.p2pml.domain.interfaces.PlaybackProvider
import com.novage.p2pml.server.config.LocalUrlFactory
import com.novage.p2pml.server.config.ServerConfig
import com.novage.p2pml.server.ServerModule
import com.novage.p2pml.webview.HeadlessWebView
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.runBlocking

abstract class P2PMediaLoaderCore(
    private val onP2PReadyCallback: () -> Unit,
    private val onP2PReadyErrorCallback: (message: String) -> Unit,
    private val coreConfigJson : String = "{}",
    private val customEngineFileUrl: String? = null
) {
    private val serverConfig = ServerConfig()
    protected val urlFactory = LocalUrlFactory(serverConfig)
    protected val eventEmitter = EventEmitter()

    protected var engineManager: P2PEngine? = null
    private var serverModule: ServerModule? = null
    private var playbackProvider: PlaybackProvider? = null

    protected var isEngineReady = false

    /**
     * Platform-agnostic initialization.
     * Call this from your platform-specific 'start' method.
     */
    protected fun initialize(
        webView: HeadlessWebView,
        provider: PlaybackProvider
    ) {
        if (engineManager != null) return

        this.playbackProvider = provider

        val engine = P2PEngineManager(webView, provider)
        this.engineManager = engine

        val module = ServerModule(
            playbackProvider = provider,
            engineManager = engine,
            urlFactory = urlFactory,
            onServerStarted = { port->
                serverConfig.updatePort(port)
                onServerStarted()
            }
        )
        this.serverModule = module

        try {
            module.start()
        } catch (e: Exception) {
            onP2PReadyErrorCallback(e.message ?: "P2P Server failed to start")
        }
    }

    fun getManifestUrl(manifestUrl: String): String {
        return urlFactory.buildManifestUrl(manifestUrl.encodeURLParameter())
    }

    fun applyDynamicConfig(dynamicCoreConfigJson: String) {
        engineManager?.applyDynamicConfig(dynamicCoreConfigJson)
    }

    open fun release() {
        eventEmitter.removeAllListeners()

        engineManager?.destroy()
        engineManager = null

        serverModule?.stop()
        serverModule = null

        runBlocking { playbackProvider?.resetData() }
        isEngineReady = false
        serverConfig.updatePort(-1)
    }

    private fun onServerStarted() {
        val engineFileUrl = customEngineFileUrl ?: urlFactory.buildStaticPageUrl()
        engineManager?.loadUrl(engineFileUrl)
    }

    protected fun onWebViewLoaded() {
        engineManager?.initCoreEngine(
            coreConfigJson = coreConfigJson,
            uploadUrl = urlFactory.buildUploadUrl()
        )

        val subscribedEvents = eventEmitter.getSubscribedEventNames()
        subscribedEvents.forEach { eventName ->
            engineManager?.subscribeToP2PEvent(eventName)
        }

        isEngineReady = true
        onP2PReadyCallback()
    }

}