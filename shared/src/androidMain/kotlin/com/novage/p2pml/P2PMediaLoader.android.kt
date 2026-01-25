package com.novage.p2pml

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.interop.OnError
import com.novage.p2pml.api.interop.OnReady
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.internal.providers.DefaultPlaybackProvider
import com.novage.p2pml.internal.providers.ExoPlayerPlaybackProvider
import com.novage.p2pml.internal.webview.AndroidWebViewFactory

class P2PMediaLoader @JvmOverloads constructor(
    private val context: Context,
    onReady: OnReady,
    onError: OnError,
    coreConfigJson: String = "{}",
    customEngineUrl: String? = null
) : P2PMediaLoaderCore(
    onReady = { onReady.onReady() },
    onError = { errorType, message -> onError.onError(errorType, message) },
    coreConfigJson = coreConfigJson,
    customEngineUrl = customEngineUrl
) {

    companion object {
        fun enableLogging() = P2PMediaLoaderCore.enableLogging()
        fun disableLogging() = P2PMediaLoaderCore.disableLogging()
    }

    private fun startInternal(provider: PlaybackProvider) {
        initialize(provider) {
            AndroidWebViewFactory(context).createHeadlessWebView(
                eventEmitter = eventEmitter,
                onWebViewLoaded = ::onWebViewLoaded,
                onWebViewError = ::failInitialization
            )
        }
    }

    /**
     * Initializes and starts P2P media streaming components.
     *
     * @param getPlaybackInfo Function to retrieve playback information
     * @throws IllegalStateException if called in an invalid state
     */
    fun start(getPlaybackInfo: () -> PlaybackInfo) {
        startInternal(DefaultPlaybackProvider(getPlaybackInfo))
    }

    /**
     * Initializes and starts P2P media streaming components.
     *
     * @param exoPlayer ExoPlayer instance for media playback
     * @throws IllegalStateException if called in an invalid state
     */
    fun start(exoPlayer: ExoPlayer) {
        startInternal(ExoPlayerPlaybackProvider(exoPlayer))
    }
}
