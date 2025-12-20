package com.novage.p2pml

import com.novage.p2pml.engine.P2PEngine
import com.novage.p2pml.engine.P2PEngineManager
import com.novage.p2pml.eventEmitter.*
import com.novage.p2pml.providers.PlaybackProvider
import com.novage.p2pml.server.LocalUrlFactory
import com.novage.p2pml.server.ServerConfig
import com.novage.p2pml.server.ServerModule
import com.novage.p2pml.webview.HeadlessWebView
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.runBlocking

abstract class P2PMediaLoaderCore(
    private val onP2PReadyCallback: () -> Unit
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
            onServerStarted = { onServerStarted() }
        )
        this.serverModule = module

        module.start()
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
        val staticUrl = urlFactory.buildStaticPageUrl()
        engineManager?.loadUrl(staticUrl)
    }

    protected fun onWebViewLoaded() {
        engineManager?.initCoreEngine(
            coreConfigJson = "{}",
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