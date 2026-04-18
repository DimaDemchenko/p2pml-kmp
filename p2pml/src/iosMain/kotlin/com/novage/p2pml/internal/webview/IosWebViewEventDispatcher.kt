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
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val onPageReady: (() -> Unit)? = null
) : NSObject(),
    WKScriptMessageHandlerProtocol {

    private val logger = CoreLogger("IosWebViewEventDispatcher")

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        val body = didReceiveScriptMessage.body as? NSDictionary ?: return
        val type = body.objectForKey("type") as? String ?: return

        when (type) {
            "onWebViewLoaded" -> onPageReady?.invoke()
            "onChunkDownloaded" -> handleChunkDownloaded(body)
            "onChunkUploaded" -> handleChunkUploaded(body)
            else -> handleComplexEvent(type, body)
        }
    }

    private fun handleChunkDownloaded(body: NSDictionary) {
        val payload = body.objectForKey("payload") as? NSDictionary ?: return
        val bytesLength = (payload.objectForKey("bytesLength") as? Number)?.toInt() ?: return
        val downloadSource = payload.objectForKey("downloadSource") as? String ?: return
        val peerId = payload.objectForKey("peerId") as? String

        events.emitChunkDownloaded(
            ChunkDownloadedDetails(bytesLength, downloadSource, peerId)
        )
    }

    private fun handleChunkUploaded(body: NSDictionary) {
        val payload = body.objectForKey("payload") as? NSDictionary ?: return
        val bytesLength = (payload.objectForKey("bytesLength") as? Number)?.toInt() ?: return
        val peerId = payload.objectForKey("peerId") as? String ?: return

        events.emitChunkUploaded(ChunkUploadedDetails(bytesLength, peerId))
    }

    private fun handleComplexEvent(type: String, body: NSDictionary) {
        val payloadDict = body.objectForKey("payload") as? NSDictionary ?: return

        runCatching {
            val jsonString = dictionaryToJson(payloadDict).toString()
            events.dispatchEventFromJsonString(type, jsonString, json)
        }.onFailure { e ->
            logger.e { "Failed to process complex event '$type': ${e.message}" }
        }
    }
}
