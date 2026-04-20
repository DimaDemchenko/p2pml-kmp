package com.novage.p2pml

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.internal.providers.DefaultPlaybackProvider
import com.novage.p2pml.internal.providers.ExoPlayerPlaybackProvider
import com.novage.p2pml.internal.webview.AndroidWebViewFactory
import kotlinx.coroutines.CancellationException

class P2PMediaLoader @JvmOverloads constructor(
    private val context: Context,
    coreConfig: CoreConfig = CoreConfig(),
    customEngineUrl: String? = null
) : P2PMediaLoaderCore(
    coreConfig = coreConfig,
    customEngineUrl = customEngineUrl
) {

    companion object {
        fun enableLogging() = P2PMediaLoaderCore.enableLogging()
        fun disableLogging() = P2PMediaLoaderCore.disableLogging()
    }

    /**
     * Initializes and starts P2P media streaming components.
     *
     * @param getPlaybackInfo Function to retrieve playback information
     * @throws IllegalStateException if called in an invalid state
     */
    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    suspend fun start(getPlaybackInfo: () -> PlaybackInfo) {
        start(DefaultPlaybackProvider(getPlaybackInfo)) { onLoaded, onError ->
            AndroidWebViewFactory(context).createHeadlessWebView(
                events = events,
                onWebViewLoaded = onLoaded,
                onWebViewError = onError
            )
        }
    }

    /**
     * Initializes and starts P2P media streaming components.
     *
     * @param exoPlayer ExoPlayer instance for media playback
     * @throws IllegalStateException if called in an invalid state
     */
    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    suspend fun start(exoPlayer: ExoPlayer) {
        start(ExoPlayerPlaybackProvider(exoPlayer)) { onLoaded, onError ->
            AndroidWebViewFactory(context).createHeadlessWebView(
                events = events,
                onWebViewLoaded = onLoaded,
                onWebViewError = onError
            )
        }
    }
}
