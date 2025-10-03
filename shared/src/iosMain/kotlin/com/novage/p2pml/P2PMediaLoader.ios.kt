package com.novage.p2pml

import com.novage.p2pml.providers.DefaultPlaybackProvider
import com.novage.p2pml.server.ServerModule
import com.novage.p2pml.webview.IOSWebView
import com.novage.p2pml.webview.WebViewManagerImpl
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.darwin.NSObject

actual class PlatformWebView(val webView: WKWebView)

actual class P2PMediaLoader(private var platformWebView: PlatformWebView) {

    private var iosWebViewManager: WebViewManagerImpl? = null

    private var serverModule: ServerModule? = null
    private var defaultPlaybackProvider: DefaultPlaybackProvider? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    fun getManifestUrl(manifestUrl: String): String {
        val encodedManifest = manifestUrl.encodeURLParameter()

        return "http://127.0.0.1:8080/manifest/$encodedManifest"
    }

    fun start(getPlaybackInfo: () -> PlaybackInfo) {
        val userController = platformWebView.webView.configuration.userContentController
        val scriptMessageHandler = MyScriptMessageHandler { _ -> onWebViewLoaded() }

        userController.addScriptMessageHandler(scriptMessageHandler, "onWebViewLoaded")

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

class MyScriptMessageHandler(private val onMessageReceived: (String) -> Unit) :
    NSObject(), WKScriptMessageHandlerProtocol {

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val messageBody = didReceiveScriptMessage.body.toString()

        onMessageReceived(messageBody)
    }
}
