package com.novage.p2pml

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.engine.P2PEngine
import com.novage.p2pml.engine.P2PEngineManager
import com.novage.p2pml.eventEmitter.CoreEventMap
import com.novage.p2pml.eventEmitter.EventEmitter
import com.novage.p2pml.eventEmitter.EventListener
import com.novage.p2pml.providers.DefaultPlaybackProvider
import com.novage.p2pml.providers.ExoPlayerPlaybackProvider
import com.novage.p2pml.providers.PlaybackProvider
import com.novage.p2pml.server.ServerModule
import com.novage.p2pml.webview.AndroidWebViewFactory
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.runBlocking

class P2PMediaLoader(private val context: Context, private val onP2PReadyCallback: () -> Unit = {}) {
    private val eventEmitter = EventEmitter()
    private var serverModule: ServerModule? = null
    private var defaultPlaybackProvider: PlaybackProvider? = null
    private var engineManager: P2PEngine? = null
    private var isEngineReady = false

    fun getManifestUrl(manifestUrl: String): String {
        val encodedManifest = manifestUrl.encodeURLParameter()
        return "http://127.0.0.1:8080/manifest/$encodedManifest"
    }

    init {
        println("P2PMediaLoader initialized with Android context.")
    }
    /**
     * Initializes and starts P2P media streaming components.
     *
     * @param getPlaybackInfo Function to retrieve playback information
     * @throws IllegalStateException if called in an invalid state
     */
    fun start(getPlaybackInfo: () -> PlaybackInfo) {
        prepareStart(context, DefaultPlaybackProvider(getPlaybackInfo))
    }

    /**
     * Initializes and starts P2P media streaming components.
     *
     * @param exoPlayer ExoPlayer instance for media playback
     * @throws IllegalStateException if called in an invalid state
     */
    fun start(exoPlayer: ExoPlayer) {
        prepareStart(context, ExoPlayerPlaybackProvider(exoPlayer))
    }

    private fun prepareStart(
        context: Context,
        provider: PlaybackProvider,
    ) {
        val webView = AndroidWebViewFactory(context).createHeadlessWebView(eventEmitter, ) {
            onWebViewLoaded()
        }
        val engine = P2PEngineManager(webView, provider)

        engineManager = engine
        defaultPlaybackProvider = provider

        serverModule =
            ServerModule(
                playbackProvider = provider,
                engineManager = engine,
                onServerStarted = { onServerStarted() },
            )
        serverModule?.start()
    }

    fun start(getPlaybackInfo: () -> PlaybackInfo, context: Context) {
        val webView = AndroidWebViewFactory(context).createHeadlessWebView(eventEmitter) {
            onWebViewLoaded()
        }
        val playbackProvider = DefaultPlaybackProvider(getPlaybackInfo)
        val engine = P2PEngineManager(webView, playbackProvider)

        engineManager = engine
        defaultPlaybackProvider = playbackProvider

        serverModule =
            ServerModule(
                playbackProvider = playbackProvider,
                engineManager = engine,
                onServerStarted = { onServerStarted() },
            )
        println("Starting server module...")
        serverModule?.start()
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

    /**
     * Applies dynamic core configurations to the `P2PMediaLoader` engine.
     *
     * @param dynamicCoreConfigJson A JSON string containing dynamic core configurations for the P2P engine.
     * Refer to the [DynamicCoreConfig Documentation](https://novage.github.io/p2p-media-loader/docs/v2.1.0/types/p2p-media-loader-core.DynamicCoreConfig.html).
     * @throws IllegalStateException if P2PMediaLoader is not started
     */
    fun applyDynamicConfig(dynamicCoreConfigJson: String) {
        engineManager?.applyDynamicConfig(dynamicCoreConfigJson)
    }

    private fun onWebViewLoaded() {
        engineManager?.initCoreEngine("{}")

        val subscribedEvents = eventEmitter.getSubscribedEventNames()
        subscribedEvents.forEach { eventName ->
            engineManager?.subscribeToP2PEvent(eventName)
        }

        isEngineReady = true
        onP2PReadyCallback()
    }

    private fun onServerStarted() {
        engineManager?.loadUrl("http://127.0.0.1:8080/static/")
    }

    fun release() {
        eventEmitter.removeAllListeners()

        engineManager?.destroy()
        engineManager = null

        serverModule?.stop()
        serverModule = null

        runBlocking { defaultPlaybackProvider?.resetData() }
        isEngineReady = false
    }
}
