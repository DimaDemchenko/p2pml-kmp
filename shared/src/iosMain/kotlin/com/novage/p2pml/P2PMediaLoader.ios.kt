package com.novage.p2pml

import com.novage.p2pml.events.*
import com.novage.p2pml.providers.DefaultPlaybackProvider
import com.novage.p2pml.webview.IosWebViewFactory

class P2PMediaLoader(
    onP2PReadyCallback: () -> Unit,
    onP2PReadyErrorCallback: (String) -> Unit,
    coreConfigJson: String = "{}",
    customEngineFileUrl: String? = null,
) :
    P2PMediaLoaderCore(
        onP2PReadyCallback = onP2PReadyCallback,
        onP2PReadyErrorCallback = onP2PReadyErrorCallback,
        coreConfigJson,
        customEngineFileUrl,
    ) {
    constructor(
        onP2PReadyCallback: () -> Unit,
        onP2PReadyErrorCallback: (String) -> Unit,
    ) : this(onP2PReadyCallback, onP2PReadyErrorCallback, "{}", null)

    fun start(getPlaybackInfo: () -> PlaybackInfo) {
        val webViewFactory = IosWebViewFactory()
        val webView = webViewFactory.createHeadlessWebView(eventEmitter) { onWebViewLoaded() }

        val provider = DefaultPlaybackProvider(getPlaybackInfo)

        initialize(webView, provider)
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

        if (isEngineReady && isFirstListener) {
            engineManager?.subscribeToP2PEvent(event.eventName)
        }

        return object : Cancellable {
            override fun cancel() {
                eventEmitter.removeEventListener(event, listener)

                val isNowEmpty = !eventEmitter.hasListeners(event)
                if (isEngineReady && isNowEmpty) {
                    engineManager?.unsubscribeFromP2PEvent(event.eventName)
                }
            }
        }
    }
}
