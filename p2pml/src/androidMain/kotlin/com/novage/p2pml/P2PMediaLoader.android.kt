package com.novage.p2pml

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.internal.providers.DefaultPlaybackProvider
import com.novage.p2pml.internal.providers.ExoPlayerPlaybackProvider
import com.novage.p2pml.internal.webview.AndroidWebViewFactory
import kotlinx.coroutines.CancellationException

class P2PMediaLoader @JvmOverloads constructor(
    private val context: Context,
    coreConfig: CoreConfig = CoreConfig(),
    customEngineUrl: String? = null
) {
    private val core = P2PMediaLoaderCore(coreConfig, customEngineUrl)

    val events get() = core.events
    val fatalErrors get() = core.fatalErrors

    fun createPlaybackUrl(manifestUrl: String) = core.createPlaybackUrl(manifestUrl)
    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) = core.applyDynamicConfig(dynamicCoreConfig)
    fun release() = core.release()

    companion object {
        fun enableLogging() = P2PMediaLoaderCore.enableLogging()
        fun disableLogging() = P2PMediaLoaderCore.disableLogging()
    }

    /**
     * Initializes and starts P2P media streaming components.
     *
     * @param getPlaybackInfo Function to retrieve playback information
     * @throws P2PMediaLoaderException if initialization or startup fails
     * @throws CancellationException if the coroutine is cancelled
     */
    suspend fun initialize(getPlaybackInfo: () -> PlaybackInfo) {
        core.initialize(DefaultPlaybackProvider(getPlaybackInfo)) { onFatalError ->
            AndroidWebViewFactory(context).createHeadlessWebView(core.events, onFatalError)
        }
    }

    /**
     * Initializes and starts P2P media streaming components.
     *
     * @param exoPlayer ExoPlayer instance for media playback
     * @throws P2PMediaLoaderException if initialization or startup fails
     * @throws CancellationException if the coroutine is cancelled
     */
    suspend fun initialize(exoPlayer: ExoPlayer) {
        core.initialize(ExoPlayerPlaybackProvider(exoPlayer)) { onFatalError ->
            AndroidWebViewFactory(context).createHeadlessWebView(core.events, onFatalError)
        }
    }
}
