package com.novage.p2pml

import com.novage.p2pml.domain.models.PlaybackInfo
import com.novage.p2pml.providers.DefaultPlaybackProvider
import com.novage.p2pml.webview.IosWebViewFactory

class P2PMediaLoader(
    onReady: () -> Unit,
    onError: (String) -> Unit,
    coreConfigJson: String = "{}",
    customEngineUrl: String? = null
) : P2PMediaLoaderCore(
    onReady = onReady,
    onError = onError,
    coreConfigJson,
    customEngineUrl
) {
    constructor(
        onReady: () -> Unit,
        onError: (String) -> Unit
    ) : this(onReady, onError, "{}", null)

    companion object {
        fun enableLogging() = P2PMediaLoaderCore.enableLogging()
        fun disableLogging() = P2PMediaLoaderCore.disableLogging()
    }

    fun start(getPlaybackInfo: () -> PlaybackInfo) {
        val provider = DefaultPlaybackProvider(getPlaybackInfo)

        initialize(provider) {
            IosWebViewFactory().createHeadlessWebView(
                eventEmitter = eventEmitter,
                onWebViewLoaded = ::onWebViewLoaded,
                onWebViewError = { msg -> failInitialization("WebView error: $msg") }
            )
        }
    }
}
