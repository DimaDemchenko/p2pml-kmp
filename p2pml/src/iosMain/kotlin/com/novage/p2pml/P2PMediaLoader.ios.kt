package com.novage.p2pml

import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.internal.core.P2PMediaLoaderCore
import com.novage.p2pml.internal.playback.AVPlayerPlaybackProvider
import com.novage.p2pml.internal.webview.IosWebViewFactory
import kotlinx.coroutines.CancellationException
import platform.AVFoundation.AVPlayer

/**
 * Entry point for P2P-accelerated media streaming on iOS.
 *
 * This class is **single-use**: after [release] is called, this instance cannot be re-initialized.
 * Create a new [P2PMediaLoader] instance if you need to restart P2P streaming.
 *
 * @param coreConfig Engine configuration. Defaults are used when omitted.
 * @param customEngineUrl URL to a custom-hosted engine page. Uses the bundled asset by default.
 */
class P2PMediaLoader(coreConfig: CoreConfig = CoreConfig(), customEngineUrl: String? = null) {

    private val core = P2PMediaLoaderCore(coreConfig, customEngineUrl)
    private var defaultProvider: AVPlayerPlaybackProvider? = null

    val events get() = core.events
    val runtimeErrors get() = core.runtimeErrors

    @Throws(P2PMediaLoaderException::class)
    fun createPlaybackUrl(manifestUrl: String) = core.createPlaybackUrl(manifestUrl)

    @Throws(P2PMediaLoaderException::class)
    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) = core.applyDynamicConfig(dynamicCoreConfig)

    fun release() {
        core.release()
        defaultProvider?.release()
        defaultProvider = null
    }

    companion object {
        fun enableLogging() = P2PMediaLoaderCore.enableLogging()
        fun disableLogging() = P2PMediaLoaderCore.disableLogging()
    }

    /**
     * Initializes and starts P2P media streaming components.
     *
     * The media loader automatically creates and manages the lifecycle of the internal playback
     * provider for [AVPlayer], and will release it when [release] is called on this loader.
     *
     * @param player AVPlayer instance for media playback
     * @throws P2PMediaLoaderException if initialization or startup fails
     * @throws CancellationException if the coroutine is cancelled
     */
    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    suspend fun initialize(player: AVPlayer) {
        val provider = AVPlayerPlaybackProvider(player)
        try {
            core.initialize(provider, IosWebViewFactory())
        } catch (e: P2PMediaLoaderException) {
            provider.release()
            throw e
        } catch (e: CancellationException) {
            provider.release()
            throw e
        }

        defaultProvider = provider
    }

    /**
     * Initializes and starts P2P media streaming components with a custom playback provider.
     *
     * **Important:** When passing a custom [PlaybackProvider], the caller retains ownership of
     * its lifecycle and is responsible for calling [PlaybackProvider.release] when the provider
     * is no longer needed. The media loader will not release custom providers automatically.
     *
     * @param provider Custom Playback Provider
     * @throws P2PMediaLoaderException if initialization or startup fails
     * @throws CancellationException if the coroutine is cancelled
     */
    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    suspend fun initialize(provider: PlaybackProvider) {
        core.initialize(provider, IosWebViewFactory())
    }
}
