package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.P2PEventRegistry
import com.novage.p2pml.api.models.ChunkDownloadedDetails
import com.novage.p2pml.api.models.ChunkUploadedDetails
import kotlinx.serialization.json.Json
import platform.Foundation.NSDictionary
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.darwin.NSObject

internal object IosBridgeChannels {
    const val GENERIC_EVENTS = "p2pml"
    const val CHUNK_DOWNLOADED = "p2pml_onChunkDownloaded"
    const val CHUNK_UPLOADED = "p2pml_onChunkUploaded"

    val all = listOf(GENERIC_EVENTS, CHUNK_DOWNLOADED, CHUNK_UPLOADED)
}

internal class IosWebViewEventDispatcher(
    private val events: P2PEventRegistry,
    json: Json = Json { ignoreUnknownKeys = true },
    onPageReady: (() -> Unit)? = null
) : NSObject(),
    WKScriptMessageHandlerProtocol {

    private val router = WebViewMessageRouter(events, json, onPageReady)

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        when (didReceiveScriptMessage.name) {
            IosBridgeChannels.CHUNK_DOWNLOADED -> handleChunkDownloaded(didReceiveScriptMessage.body)
            IosBridgeChannels.CHUNK_UPLOADED -> handleChunkUploaded(didReceiveScriptMessage.body)
            IosBridgeChannels.GENERIC_EVENTS -> handleGenericMessage(didReceiveScriptMessage.body)
        }
    }

    private fun handleChunkDownloaded(body: Any?) {
        val dict = body as? NSDictionary ?: return
        val bytesLength = (dict.objectForKey("bytesLength") as? Number)?.toInt() ?: return
        val downloadSource = dict.objectForKey("downloadSource") as? String ?: return
        val peerId = dict.objectForKey("peerId") as? String
        events.emitChunkDownloaded(ChunkDownloadedDetails(bytesLength, downloadSource, peerId))
    }

    private fun handleChunkUploaded(body: Any?) {
        val dict = body as? NSDictionary ?: return
        val bytesLength = (dict.objectForKey("bytesLength") as? Number)?.toInt() ?: return
        val peerId = dict.objectForKey("peerId") as? String ?: return
        events.emitChunkUploaded(ChunkUploadedDetails(bytesLength, peerId))
    }

    private fun handleGenericMessage(body: Any?) {
        val messageString = body as? String ?: return
        router.handleMessage(messageString)
    }
}
