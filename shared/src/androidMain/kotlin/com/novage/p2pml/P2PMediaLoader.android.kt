package com.novage.p2pml

import android.content.Context
import com.novage.p2pml.engine.P2PEngineManager
import com.novage.p2pml.eventEmitter.EventEmitter
import com.novage.p2pml.providers.DefaultPlaybackProvider
import com.novage.p2pml.server.ServerModule
import com.novage.p2pml.webview.AndroidWebViewFactory
import io.ktor.http.encodeURLParameter

actual class P2PMediaLoader {
    private val eventEmitter = EventEmitter()
    private var serverModule: ServerModule? = null
    private var defaultPlaybackProvider: DefaultPlaybackProvider? = null

    fun getManifestUrl(manifestUrl: String): String {
        val encodedManifest = manifestUrl.encodeURLParameter()
        val newUrl = "http://127.0.0.1:8080/manifest/$encodedManifest"
        return newUrl
    }

    fun start(getPlaybackInfo: () -> PlaybackInfo, context: Context) {
        val webView = AndroidWebViewFactory(context).createHeadlessWebView(eventEmitter, ) {
            onWebViewLoaded()
        }
        val playbackProvider = DefaultPlaybackProvider(getPlaybackInfo)
        val engineManager = P2PEngineManager(webView, playbackProvider)
        serverModule =
            ServerModule(
                playbackProvider = playbackProvider,
                engineManager = engineManager,
                onServerStarted = { onServerStarted() },
            )
        serverModule?.start()
    }

    private fun onWebViewLoaded() {
        println("WebView loaded")
    }

    private fun onServerStarted() {
        println("Server started")

    }
}
