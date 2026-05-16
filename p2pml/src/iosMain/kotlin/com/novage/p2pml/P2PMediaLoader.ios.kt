package com.novage.p2pml

import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.internal.providers.AVPlayerPlaybackProvider
import com.novage.p2pml.internal.webview.IosWebViewFactory
import kotlinx.coroutines.CancellationException
import platform.AVFoundation.AVPlayer

/**
 * Entry point for P2P-accelerated media streaming on iOS.
 *
 * This class is **single-use**: after [release] is called, this instance cannot be re-initialized.
 * Create a new [P2PMediaLoader] instance if you need to restart P2P streaming.
 */
class P2PMediaLoader(coreConfig: CoreConfig = CoreConfig(), customEngineUrl: String? = null) {

    private val core = P2PMediaLoaderCore(coreConfig, customEngineUrl)

    val events get() = core.events
    val fatalErrors get() = core.fatalErrors

    @Throws(P2PMediaLoaderException::class)
    fun createPlaybackUrl(manifestUrl: String) = core.createPlaybackUrl(manifestUrl)

    @Throws(P2PMediaLoaderException::class)
    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) = core.applyDynamicConfig(dynamicCoreConfig)

    fun release() = core.release()

    companion object {
        fun enableLogging() = P2PMediaLoaderCore.enableLogging()
        fun disableLogging() = P2PMediaLoaderCore.disableLogging()
    }

    @Throws(P2PMediaLoaderException::class)
    suspend fun initialize(player: AVPlayer) {
        val provider = AVPlayerPlaybackProvider(player)
        core.initialize(provider, IosWebViewFactory())
    }

    @Throws(P2PMediaLoaderException::class)
    suspend fun initialize(provider: PlaybackProvider) {
        core.initialize(provider, IosWebViewFactory())
    }
}
