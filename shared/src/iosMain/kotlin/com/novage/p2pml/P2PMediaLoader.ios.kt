package com.novage.p2pml

import com.novage.p2pml.eventEmitter.*
import com.novage.p2pml.providers.DefaultPlaybackProvider
import com.novage.p2pml.server.ServerModule
import com.novage.p2pml.webview.IOSWebView
import com.novage.p2pml.webview.WebViewEventDispatcher
import com.novage.p2pml.webview.WebViewManagerImpl
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.WebKit.WKWebView

actual class PlatformWebView(val webView: WKWebView)

actual class P2PMediaLoader(private var platformWebView: PlatformWebView) {

    private val eventEmitter = EventEmitter()

    private var iosWebViewManager: WebViewManagerImpl? = null

    private var serverModule: ServerModule? = null
    private var defaultPlaybackProvider: DefaultPlaybackProvider? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    /**
     * Adds an event listener to the P2P engine.
     *
     * @param event Event type to listen for
     * @param listener Callback function to invoke when the event occurs
     */
    fun <T> addEventListener(event: CoreEventMap<T>, listener: EventListener<T>) {
        eventEmitter.addEventListener(event, listener)
    }

    /**
     * Removes an event listener from the P2P engine.
     *
     * @param event Event type to remove the listener from
     * @param listener Callback function to remove
     */
    fun <T> removeEventListener(event: CoreEventMap<T>, listener: EventListener<T>) {
        eventEmitter.removeEventListener(event, listener)
    }

    fun getManifestUrl(manifestUrl: String): String {
        val encodedManifest = manifestUrl.encodeURLParameter()

        return "http://127.0.0.1:8080/manifest/$encodedManifest"
    }

    fun start(getPlaybackInfo: () -> PlaybackInfo) {
        val userController = platformWebView.webView.configuration.userContentController
        val scriptMessageHandler = WebViewEventDispatcher(eventEmitter) { onWebViewLoaded() }

        userController.addScriptMessageHandler(scriptMessageHandler, "p2pml")

        val playbackProvider = DefaultPlaybackProvider(getPlaybackInfo)

        val iosWebView = IOSWebView(platformWebView.webView)
        iosWebViewManager = WebViewManagerImpl(iosWebView, playbackProvider, coroutineScope)

        defaultPlaybackProvider = playbackProvider
        serverModule =
            ServerModule(
                playbackProvider = playbackProvider,
                webViewManager = iosWebViewManager!!,
                onServerStarted = { onServerStarted() },
            )

        serverModule?.start()
    }

    private fun onServerStarted() {
        iosWebViewManager?.loadUrl("http://127.0.0.1:8080/static/")
    }

    private fun onWebViewLoaded() {
        coroutineScope.launch { iosWebViewManager?.initCoreEngine("{}") }
    }
}
