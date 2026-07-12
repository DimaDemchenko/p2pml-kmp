package com.novage.p2pml.internal.webview

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.novage.p2pml.api.events.ChunkDownloadedDetails
import com.novage.p2pml.api.events.ChunkUploadedDetails
import com.novage.p2pml.api.events.DownloadSource
import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.serialization.json.Json

internal class AndroidWebViewEventDispatcher(
    private val events: P2PEvents,
    json: Json = Json { ignoreUnknownKeys = true },
    onPageReady: (() -> Unit)? = null
) {
    private val logger = CoreLogger("AndroidWebViewEventDispatcher")
    private val mainHandler = Handler(Looper.getMainLooper())
    private val router = WebViewMessageRouter(events, json) {
        mainHandler.post { onPageReady?.invoke() }
    }

    @JavascriptInterface
    fun onChunkDownloaded(bytesLength: Int, downloadSource: String, peerId: String?) {
        val source = DownloadSource.fromValue(downloadSource) ?: run {
            logger.w { "Dropping chunk event with unknown download source: $downloadSource" }
            return
        }
        events.emitChunkDownloaded(ChunkDownloadedDetails(bytesLength, source, peerId))
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
