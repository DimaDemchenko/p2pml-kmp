package com.novage.p2pml

import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.internal.providers.DefaultPlaybackProvider
import com.novage.p2pml.internal.webview.IosWebViewFactory

class P2PMediaLoader(
    onReady: () -> Unit,
    onError: (MediaLoaderErrorType, String) -> Unit,
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
        onError: (MediaLoaderErrorType, String) -> Unit
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
                onWebViewError = ::failInitialization
            )
        }
    }
}
