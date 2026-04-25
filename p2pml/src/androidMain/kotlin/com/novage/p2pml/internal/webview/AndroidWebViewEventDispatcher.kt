package com.novage.p2pml.internal.webview

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.novage.p2pml.api.events.P2PEventRegistry
import com.novage.p2pml.api.models.ChunkDownloadedDetails
import com.novage.p2pml.api.models.ChunkUploadedDetails
import kotlinx.serialization.json.Json

internal class AndroidWebViewEventDispatcher(
    private val events: P2PEventRegistry,
    json: Json = Json { ignoreUnknownKeys = true },
    onPageReady: (() -> Unit)? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val router = WebViewMessageRouter(events, json) {
        mainHandler.post { onPageReady?.invoke() }
    }

    @JavascriptInterface
    fun onChunkDownloaded(bytesLength: Int, downloadSource: String, peerId: String?) {
        events.emitChunkDownloaded(ChunkDownloadedDetails(bytesLength, downloadSource, peerId))
    }

    @JavascriptInterface
    fun onChunkUploaded(bytesLength: Int, peerId: String) {
        events.emitChunkUploaded(ChunkUploadedDetails(bytesLength, peerId))
    }

    @JavascriptInterface
    fun postMessage(message: String) {
        router.handleMessage(message)
    }
}
