package com.novage.p2pml

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.internal.providers.ExoPlayerPlaybackProvider
import com.novage.p2pml.internal.webview.AndroidWebViewFactory
import kotlinx.coroutines.CancellationException

/**
 * Entry point for P2P-accelerated media streaming on Android.
 *
 * This class is **single-use**: after [release] is called, this instance cannot be re-initialized.
 * Create a new [P2PMediaLoader] instance if you need to restart P2P streaming.
 */
class P2PMediaLoader @JvmOverloads constructor(
    private val context: Context,
    coreConfig: CoreConfig = CoreConfig(),
    customEngineUrl: String? = null
) {
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

    /**
     * Initializes and starts P2P media streaming components.
     *
     * @param exoPlayer ExoPlayer instance for media playback
     * @throws P2PMediaLoaderException if initialization or startup fails
     * @throws CancellationException if the coroutine is cancelled
     */
    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    suspend fun initialize(exoPlayer: ExoPlayer) {
        core.initialize(ExoPlayerPlaybackProvider(exoPlayer), AndroidWebViewFactory(context))
    }

    /**
     * Initializes and starts P2P media streaming components with a custom playback provider.
     *
     * @param provider Custom Playback Provider
     * @throws P2PMediaLoaderException if initialization or startup fails
     * @throws CancellationException if the coroutine is cancelled
     */
    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    suspend fun initialize(provider: PlaybackProvider) {
        core.initialize(provider, AndroidWebViewFactory(context))
    }
}
