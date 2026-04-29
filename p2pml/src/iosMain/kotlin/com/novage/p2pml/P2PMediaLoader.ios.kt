package com.novage.p2pml

import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.internal.providers.DefaultPlaybackProvider
import com.novage.p2pml.internal.webview.IosWebViewFactory
import kotlinx.coroutines.CancellationException

class P2PMediaLoader(coreConfig: CoreConfig = CoreConfig(), customEngineUrl: String? = null) {

    private val core = P2PMediaLoaderCore(coreConfig, customEngineUrl)

    val events get() = core.events
    val fatalErrors get() = core.fatalErrors

    @Throws(P2PMediaLoaderException::class)
    fun createPlaybackUrl(manifestUrl: String) = core.createPlaybackUrl(manifestUrl)

    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) = core.applyDynamicConfig(dynamicCoreConfig)

    fun release() = core.release()

    companion object {
        fun enableLogging() = P2PMediaLoaderCore.enableLogging()
        fun disableLogging() = P2PMediaLoaderCore.disableLogging()
    }

    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    suspend fun initialize(getPlaybackInfo: () -> PlaybackInfo) {
        val provider = DefaultPlaybackProvider(getPlaybackInfo)

        core.initialize(provider) { onLoaded, onError ->
            IosWebViewFactory().createHeadlessWebView(
                events = core.events,
                onWebViewLoaded = onLoaded,
                onWebViewError = onError
            )
        }
    }
}
