package com.novage.p2pml

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.api.config.CoreConfig
import com.novage.p2pml.api.config.DynamicCoreConfig
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.playback.PlaybackProvider
import com.novage.p2pml.internal.core.P2PMediaLoaderCore
import com.novage.p2pml.internal.playback.ExoPlayerPlaybackProvider
import com.novage.p2pml.internal.webview.AndroidWebViewFactory
import kotlinx.coroutines.CancellationException

/**
 * Entry point for P2P-accelerated media streaming on Android.
 *
 * This class is **single-use**: after [release] is called, this instance cannot be re-initialized.
 * Create a new [P2PMediaLoader] instance if you need to restart P2P streaming.
 *
 * @param context Android context.
 * @param coreConfig Engine configuration. Defaults are used when omitted.
 * @param customEngineUrl URL to a custom-hosted engine page. Uses the bundled asset by default.
 *   Custom pages do not include the bundled page's log bridge, so engine console output and
 *   uncaught JS errors are not forwarded to the native log unless the page replicates it.
 */
class P2PMediaLoader @JvmOverloads constructor(
    context: Context,
    coreConfig: CoreConfig = CoreConfig(),
    customEngineUrl: String? = null
) {
    private val appContext: Context = context.applicationContext
    private val core = P2PMediaLoaderCore(coreConfig, customEngineUrl)
    private var defaultProvider: ExoPlayerPlaybackProvider? = null

    val p2pEvents get() = core.p2pEvents

    /**
     * Observable, latched loader state. On [com.novage.p2pml.api.state.P2PMediaLoaderStatus.FAILED],
     * the local proxy is gone — fall back to the origin URL.
     */
    val state get() = core.state

    @Throws(P2PMediaLoaderException::class)
    fun createPlaybackUrl(manifestUrl: String) = core.createPlaybackUrl(manifestUrl)

    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) = core.applyDynamicConfig(dynamicCoreConfig)

    fun release() {
        core.release()
        defaultProvider?.release()
        defaultProvider = null
    }

    companion object {
        /**
         * Lowers [com.novage.p2pml.api.logging.P2PLogging.minLevel] to DEBUG for full diagnostics.
         * Debug output includes manifest and segment URLs, which may carry signed query parameters.
         */
        fun enableLogging() = P2PMediaLoaderCore.enableLogging()

        /**
         * Restores the default log verbosity (WARN and above). To silence the library entirely,
         * set [com.novage.p2pml.api.logging.P2PLogging.sink] to null instead.
         */
        fun disableLogging() = P2PMediaLoaderCore.disableLogging()
    }

    /**
     * Initializes and starts P2P media streaming components.
     *
     * The media loader automatically creates and manages the lifecycle of the internal playback
     * provider for [ExoPlayer], and will release it when [release] is called on this loader.
     *
     * @param exoPlayer ExoPlayer instance for media playback
     * @throws P2PMediaLoaderException if initialization or startup fails
     * @throws CancellationException if the coroutine is cancelled
     */
    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    suspend fun initialize(exoPlayer: ExoPlayer) {
        val provider = ExoPlayerPlaybackProvider(exoPlayer)
        try {
            core.initialize(provider, AndroidWebViewFactory(appContext))
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
        core.initialize(provider, AndroidWebViewFactory(appContext))
    }
}
