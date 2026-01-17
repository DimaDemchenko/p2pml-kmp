package com.novage.p2pml

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.domain.interfaces.PlaybackProvider
import com.novage.p2pml.domain.models.PlaybackInfo
import com.novage.p2pml.interop.OnP2PReadyCallback
import com.novage.p2pml.interop.OnP2PReadyErrorCallback
import com.novage.p2pml.providers.DefaultPlaybackProvider
import com.novage.p2pml.providers.ExoPlayerPlaybackProvider
import com.novage.p2pml.webview.AndroidWebViewFactory

class P2PMediaLoader @JvmOverloads constructor(
    private val context: Context,
    onReady: OnP2PReadyCallback,
    onError: OnP2PReadyErrorCallback,
    coreConfigJson: String = "{}",
    customEngineUrl: String? = null
) : P2PMediaLoaderCore(
    onReady = { onReady.onReady() },
    onError = { message -> onError.onError(message) },
    coreConfigJson = coreConfigJson,
    customEngineUrl = customEngineUrl
) {

    companion object {
        fun enableLogging() = P2PMediaLoaderCore.enableLogging()
        fun disableLogging() = P2PMediaLoaderCore.disableLogging()
    }

    private fun startInternal(provider: PlaybackProvider) {
        val webView = AndroidWebViewFactory(context).createHeadlessWebView(
            eventEmitter = eventEmitter,
            onWebViewLoaded = ::onWebViewLoaded,
            onWebViewError = { errorMessage ->
                failInitialization("WebView failed to load: $errorMessage")
            }
        )

        initialize(webView, provider)
    }

    /**
     * Initializes and starts P2P media streaming components.
     *
     * @param getPlaybackInfo Function to retrieve playback information
     * @throws IllegalStateException if called in an invalid state
     */
    fun start(getPlaybackInfo: () -> PlaybackInfo) {
        val provider = DefaultPlaybackProvider(getPlaybackInfo)
        startInternal(provider)
    }

    /**
     * Initializes and starts P2P media streaming components.
     *
     * @param exoPlayer ExoPlayer instance for media playback
     * @throws IllegalStateException if called in an invalid state
     */
    fun start(exoPlayer: ExoPlayer) {
        val provider = ExoPlayerPlaybackProvider(exoPlayer)
        startInternal(provider)
    }
}
