package com.novage.p2pml

import com.novage.p2pml.engine.P2PEngine
import com.novage.p2pml.engine.P2PEngineManager
import com.novage.p2pml.eventEmitter.*
import com.novage.p2pml.providers.DefaultPlaybackProvider
import com.novage.p2pml.server.LocalUrlFactory
import com.novage.p2pml.server.ServerConfig
import com.novage.p2pml.server.ServerModule
import com.novage.p2pml.webview.IosWebViewFactory
import io.ktor.http.encodeURLParameter
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.runBlocking

class P2PMediaLoader(private val onP2PReadyCallback: () -> Unit = {}) {
    private val serverConfig = ServerConfig()
    private val urlFactory = LocalUrlFactory(serverConfig)
    private val eventEmitter = EventEmitter()
    private var engineManager: P2PEngine? = null
    private var serverModule: ServerModule? = null
    private var defaultPlaybackProvider: DefaultPlaybackProvider? = null
    private val isEngineReady = atomic(false)

    fun getManifestUrl(manifestUrl: String): String {
        return urlFactory.buildManifestUrl(manifestUrl.encodeURLParameter())
    }

    fun start(getPlaybackInfo: () -> PlaybackInfo) {
        val webViewFactory = IosWebViewFactory()
        val platformWebView =
            webViewFactory.createHeadlessWebView(eventEmitter) { onWebViewLoaded() }
        val playbackProvider = DefaultPlaybackProvider(getPlaybackInfo)
        val engine = P2PEngineManager(platformWebView, playbackProvider)

        defaultPlaybackProvider = playbackProvider
        engineManager = engine
        serverModule =
            ServerModule(
                playbackProvider = playbackProvider,
                engineManager = engine,
                urlFactory = urlFactory,
                onServerStarted = { port -> onServerStarted(port) },
            )

        serverModule?.start()
    }

    fun release() {
        eventEmitter.removeAllListeners()

        engineManager?.destroy()
        engineManager = null

        serverModule?.stop()
        serverModule = null

        runBlocking { defaultPlaybackProvider?.resetData() }
        isEngineReady.value = false
        serverConfig.updatePort(-1)
    }

    private fun onServerStarted(port: Int) {
        this.serverConfig.updatePort(port);

        engineManager?.loadUrl(urlFactory.buildStaticPageUrl())
    }

    private fun onWebViewLoaded() {
        engineManager?.initCoreEngine(
            coreConfigJson = "{}",
            uploadUrl = urlFactory.buildUploadUrl()
        )

        val subscribedEvents = eventEmitter.getSubscribedEventNames()
        subscribedEvents.forEach { eventName ->
            engineManager?.subscribeToP2PEvent(eventName)
        }

        isEngineReady.value = true
        onP2PReadyCallback()
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
        val isFirstListener = !eventEmitter.hasListeners(event)

        eventEmitter.addEventListener(event, listener)

        if (isEngineReady.value && isFirstListener) {
            engineManager?.subscribeToP2PEvent(event.eventName)
        }

        return object : Cancellable {
            override fun cancel() {
                eventEmitter.removeEventListener(event, listener)

                val isNowEmpty = !eventEmitter.hasListeners(event)
                if (isEngineReady.value && isNowEmpty) {
                    engineManager?.unsubscribeFromP2PEvent(event.eventName)
                }
            }
        }
    }
}
