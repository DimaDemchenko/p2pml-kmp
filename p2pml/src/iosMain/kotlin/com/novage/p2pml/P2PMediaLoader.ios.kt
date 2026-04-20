package com.novage.p2pml

import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.internal.providers.DefaultPlaybackProvider
import com.novage.p2pml.internal.webview.IosWebViewFactory
import kotlinx.coroutines.CancellationException

class P2PMediaLoader(coreConfig: CoreConfig = CoreConfig(), customEngineUrl: String? = null) :
    P2PMediaLoaderCore(
        coreConfig,
        customEngineUrl
    ) {

    companion object {
        fun enableLogging() = P2PMediaLoaderCore.enableLogging()
        fun disableLogging() = P2PMediaLoaderCore.disableLogging()
    }

    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    suspend fun start(getPlaybackInfo: () -> PlaybackInfo) {
        val provider = DefaultPlaybackProvider(getPlaybackInfo)

        start(provider) { onLoaded, onError ->
            IosWebViewFactory().createHeadlessWebView(
                events = events,
                onWebViewLoaded = onLoaded,
                onWebViewError = onError
            )
        }
    }
}
