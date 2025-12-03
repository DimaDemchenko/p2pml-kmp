package com.novage.p2pml

import com.novage.p2pml.eventEmitter.*
import com.novage.p2pml.providers.DefaultPlaybackProvider
import com.novage.p2pml.server.ServerModule
import com.novage.p2pml.webview.PlatformContext
import com.novage.p2pml.webview.PlatformWebViewFactory
import com.novage.p2pml.webview.WebViewManagerImpl
import io.ktor.http.encodeURLParameter
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

actual class P2PMediaLoader(private val onP2PReadyCallback: () -> Unit = {}) {

    private val eventEmitter = EventEmitter()

    private var iosWebViewManager: WebViewManagerImpl? = null

    private var serverModule: ServerModule? = null
    private var defaultPlaybackProvider: DefaultPlaybackProvider? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val isEngineReady = atomic(false)

    fun getManifestUrl(manifestUrl: String): String {
        val encodedManifest = manifestUrl.encodeURLParameter()

        return "http://127.0.0.1:8080/manifest/$encodedManifest"
    }

    fun start(getPlaybackInfo: () -> PlaybackInfo) {
        val platformWebViewFactory = PlatformWebViewFactory(PlatformContext())

        val platformWebView =
            platformWebViewFactory.createWebView(eventEmitter) { onWebViewLoaded() }
        val playbackProvider = DefaultPlaybackProvider(getPlaybackInfo)
        val webViewManager =
            WebViewManagerImpl(platformWebView, playbackProvider, coroutineScope)

        defaultPlaybackProvider = playbackProvider
        iosWebViewManager = webViewManager
        serverModule =
            ServerModule(
                playbackProvider = playbackProvider,
                webViewManager = webViewManager,
                onServerStarted = { onServerStarted() },
            )

        serverModule?.start()
    }

    private fun onServerStarted() {
        iosWebViewManager?.loadUrl("http://127.0.0.1:8080/static/")
    }

    private fun onWebViewLoaded() {
        coroutineScope.launch {
            iosWebViewManager?.initCoreEngine("{}")

            val subscribedEvents = eventEmitter.getSubscribedEventNames()
            subscribedEvents.forEach { eventName ->
                iosWebViewManager?.subscribeToP2PEvent(eventName)
            }

            isEngineReady.value = true
            onP2PReadyCallback()
        }
    }

    fun observeSegmentLoaded(block: (SegmentLoadDetails) -> Unit) =
        bind(CoreEventMap.OnSegmentLoaded, block)

    fun observeSegmentStart(block: (SegmentStartDetails) -> Unit) =
        bind(CoreEventMap.OnSegmentStart, block)

    fun observeSegmentError(block: (SegmentErrorDetails) -> Unit) =
        bind(CoreEventMap.OnSegmentError, block)

    fun observeSegmentAbort(block: (SegmentAbortDetails) -> Unit) =
        bind(CoreEventMap.OnSegmentAbort, block)

    fun observePeerConnect(block: (PeerDetails) -> Unit) = bind(CoreEventMap.OnPeerConnect, block)

    fun observePeerClose(block: (PeerDetails) -> Unit) = bind(CoreEventMap.OnPeerClose, block)

    fun observePeerError(block: (PeerErrorDetails) -> Unit) = bind(CoreEventMap.OnPeerError, block)

    fun observeChunkDownloaded(block: (ChunkDownloadedDetails) -> Unit) =
        bind(CoreEventMap.OnChunkDownloaded, block)

    fun observeChunkUploaded(block: (ChunkUploadedDetails) -> Unit) =
        bind(CoreEventMap.OnChunkUploaded, block)

    fun observeTrackerError(block: (TrackerErrorDetails) -> Unit) =
        bind(CoreEventMap.OnTrackerError, block)

    fun observeTrackerWarning(block: (TrackerWarningDetails) -> Unit) =
        bind(CoreEventMap.OnTrackerWarning, block)

    private fun <T> bind(event: CoreEventMap<T>, block: (T) -> Unit): Cancellable {
        val listener = EventListener<T> { block(it) }
        val initialCount = eventEmitter.getListenerCount(event)

        eventEmitter.addEventListener(event, listener)

        if (isEngineReady.value && initialCount == 0) {
            coroutineScope.launch { iosWebViewManager?.subscribeToP2PEvent(event.eventName) }
        }

        return object : Cancellable {
            override fun cancel() {
                eventEmitter.removeEventListener(event, listener)

                val remainingCount = eventEmitter.getListenerCount(event)
                if (isEngineReady.value && remainingCount == 0) {
                    coroutineScope.launch {
                        iosWebViewManager?.unsubscribeFromP2PEvent(event.eventName)
                    }
                }
            }
        }
    }
}
