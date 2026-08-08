package com.novage.p2pml.internal.webview

import android.webkit.JavascriptInterface
import com.novage.p2pml.api.events.ChunkDownloadedDetails
import com.novage.p2pml.api.events.ChunkUploadedDetails
import com.novage.p2pml.api.events.DownloadSource
import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.api.events.StreamType
import com.novage.p2pml.internal.utils.CoreLogger

internal class AndroidWebViewEventDispatcher(
    private val events: P2PEvents,
    onPageReady: () -> Unit,
    onCoreInitResult: (errorMessage: String?) -> Unit
) {
    private val logger = CoreLogger("AndroidWebViewEventDispatcher")

    // onPageReady / onCoreInitResult drive the WebView state machine, which marshals to the main
    // thread itself, so these @JavascriptInterface callbacks can hand off directly from the JS thread.
    private val router = WebViewMessageRouter(events, onPageReady = onPageReady, onCoreInitResult = onCoreInitResult)

    @JavascriptInterface
    fun onChunkDownloaded(
        bytesLength: Int,
        downloadSource: String,
        peerId: String?,
        streamType: String,
        infoHash: String
    ) {
        val source = DownloadSource.fromValue(downloadSource) ?: run {
            logger.w { "Dropping chunk event with unknown download source: $downloadSource" }
            return
        }
        val stream = resolveStreamType(streamType) ?: return
        events.emitChunkDownloaded(ChunkDownloadedDetails(bytesLength, source, peerId, stream, infoHash))
    }

    @JavascriptInterface
    fun onChunkUploaded(bytesLength: Int, peerId: String, streamType: String, infoHash: String) {
        val stream = resolveStreamType(streamType) ?: return
        events.emitChunkUploaded(ChunkUploadedDetails(bytesLength, peerId, stream, infoHash))
    }

    private fun resolveStreamType(streamType: String): StreamType? = StreamType.fromValue(streamType) ?: run {
        logger.w { "Dropping chunk event with unknown stream type: $streamType" }
        null
    }

    @JavascriptInterface
    fun postMessage(message: String) {
        router.handleMessage(message)
    }
}
