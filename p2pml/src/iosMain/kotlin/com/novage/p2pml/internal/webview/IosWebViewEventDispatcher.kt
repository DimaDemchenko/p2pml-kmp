package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.P2PEventRegistry
import com.novage.p2pml.api.models.ChunkDownloadedDetails
import com.novage.p2pml.api.models.ChunkUploadedDetails
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.utils.dictionaryToJson
import kotlinx.serialization.json.Json
import platform.Foundation.NSDictionary
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.darwin.NSObject

internal class IosWebViewEventDispatcher(
    private val events: P2PEventRegistry,
    json: Json = Json { ignoreUnknownKeys = true },
    onPageReady: (() -> Unit)? = null
) : NSObject(),
    WKScriptMessageHandlerProtocol {

    private val logger = CoreLogger("IosWebViewEventDispatcher")
    private val router = WebViewMessageRouter(events, json, onPageReady)

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        val body = didReceiveScriptMessage.body as? NSDictionary ?: return
        val type = body.objectForKey("type") as? String

        if (type == "onChunkDownloaded") {
            handleChunkDownloaded(body)
            return
        } else if (type == "onChunkUploaded") {
            handleChunkUploaded(body)
            return
        }

        val jsonString = dictionaryToJson(body).toString()
        router.handleMessage(jsonString)
    }

    private fun handleChunkDownloaded(body: NSDictionary) {
        val payload = body.objectForKey("payload") as? NSDictionary ?: return
        val bytesLength = (payload.objectForKey("bytesLength") as? Number)?.toInt() ?: return
        val downloadSource = payload.objectForKey("downloadSource") as? String ?: return
        val peerId = payload.objectForKey("peerId") as? String

        events.emitChunkDownloaded(ChunkDownloadedDetails(bytesLength, downloadSource, peerId))
    }

    private fun handleChunkUploaded(body: NSDictionary) {
        val payload = body.objectForKey("payload") as? NSDictionary ?: return
        val bytesLength = (payload.objectForKey("bytesLength") as? Number)?.toInt() ?: return
        val peerId = payload.objectForKey("peerId") as? String ?: return

        events.emitChunkUploaded(ChunkUploadedDetails(bytesLength, peerId))
    }
}
