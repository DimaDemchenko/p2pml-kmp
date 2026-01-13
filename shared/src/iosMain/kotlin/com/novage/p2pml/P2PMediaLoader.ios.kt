package com.novage.p2pml

import com.novage.p2pml.domain.models.PlaybackInfo
import com.novage.p2pml.providers.DefaultPlaybackProvider
import com.novage.p2pml.webview.IosWebViewFactory

class P2PMediaLoader(
    onP2PReadyCallback: () -> Unit,
    onP2PReadyErrorCallback: (String) -> Unit,
    coreConfigJson: String = "{}",
    customEngineFileUrl: String? = null
) : P2PMediaLoaderCore(
    onP2PReadyCallback = onP2PReadyCallback,
    onP2PReadyErrorCallback = onP2PReadyErrorCallback,
    coreConfigJson,
    customEngineFileUrl
) {
    constructor(
        onP2PReadyCallback: () -> Unit,
        onP2PReadyErrorCallback: (String) -> Unit
    ) : this(onP2PReadyCallback, onP2PReadyErrorCallback, "{}", null)

    companion object {
        fun enableLogging() = P2PMediaLoaderCore.enableLogging()
        fun disableLogging() = P2PMediaLoaderCore.disableLogging()
    }

    fun start(getPlaybackInfo: () -> PlaybackInfo) {
        val webViewFactory = IosWebViewFactory()
        val webView = webViewFactory.createHeadlessWebView(
            eventEmitter = eventEmitter,
            onWebViewLoaded = ::onWebViewLoaded,
            onWebViewError = { errorMessage ->
                failInitialization("WebView failed to load: $errorMessage")
            }
        )

        val provider = DefaultPlaybackProvider(getPlaybackInfo)

        initialize(webView, provider)
    }
}
