package com.novage.p2pml

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.domain.models.CoreEventMap
import com.novage.p2pml.domain.models.PlaybackInfo
import com.novage.p2pml.events.EventListener
import com.novage.p2pml.interop.OnP2PReadyCallback
import com.novage.p2pml.interop.OnP2PReadyErrorCallback
import com.novage.p2pml.providers.DefaultPlaybackProvider
import com.novage.p2pml.providers.ExoPlayerPlaybackProvider
import com.novage.p2pml.domain.interfaces.PlaybackProvider
import com.novage.p2pml.webview.AndroidWebViewFactory

class P2PMediaLoader(
    private val context: Context,
    onP2PReadyCallback: OnP2PReadyCallback,
    onP2PReadyErrorCallback: OnP2PReadyErrorCallback,
    coreConfigJson: String = "{}",
    customEngineFileUrl: String? = null
) : P2PMediaLoaderCore(
    onP2PReadyCallback = { onP2PReadyCallback.onReady() },
    onP2PReadyErrorCallback = { message -> onP2PReadyErrorCallback.onError(message) },
    coreConfigJson = coreConfigJson,
    customEngineFileUrl = customEngineFileUrl
) {

    companion object {
        fun enableLogging() = P2PMediaLoaderCore.enableLogging()
        fun disableLogging() = P2PMediaLoaderCore.disableLogging()
    }

    private fun startInternal(provider: PlaybackProvider) {
        val webView = AndroidWebViewFactory(context).createHeadlessWebView(eventEmitter) {
            onWebViewLoaded()
        }

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

    /**
     * Adds an event listener to the P2P engine.
     *
     * @param event Event type to listen for
     * @param listener Callback function to invoke when the event occurs
     */
    fun <T> addEventListener(
        event: CoreEventMap<T>,
        listener: EventListener<T>,
    ) {
        val isFirstListener = !eventEmitter.hasListeners(event)
        eventEmitter.addEventListener(event, listener)

        if (isEngineReady && isFirstListener) {
            engineManager?.subscribeToP2PEvent(event.eventName)
        }
    }

    /**
     * Removes an event listener from the P2P engine.
     *
     * @param event Event type to remove the listener from
     * @param listener Callback function to remove
     */
    fun <T> removeEventListener(
        event: CoreEventMap<T>,
        listener: EventListener<T>,
    ) {
        eventEmitter.removeEventListener(event, listener)

        val isNowEmpty = !eventEmitter.hasListeners(event)
        if (isEngineReady && isNowEmpty) {
            engineManager?.unsubscribeFromP2PEvent(event.eventName)
        }
    }
}
