package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.P2PEventRegistry
import com.novage.p2pml.api.models.ChunkDownloadedDetails
import com.novage.p2pml.api.models.ChunkUploadedDetails
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.utils.decodeFromNSDictionary
import kotlinx.serialization.SerializationException
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

    @Suppress("CyclomaticComplexMethod")
    private fun handleComplexEvent(type: String, body: NSDictionary) {
        val payloadDict = body.objectForKey("payload") as? NSDictionary ?: return

        try {
            when (type) {
                "onSegmentLoaded" -> events.emitSegmentLoaded(json.decodeFromNSDictionary(payloadDict))
                "onSegmentStart" -> events.emitSegmentStart(json.decodeFromNSDictionary(payloadDict))
                "onSegmentAbort" -> events.emitSegmentAbort(json.decodeFromNSDictionary(payloadDict))
                "onSegmentError" -> events.emitSegmentError(json.decodeFromNSDictionary(payloadDict))
                "onPeerConnect" -> events.emitPeerConnect(json.decodeFromNSDictionary(payloadDict))
                "onPeerClose" -> events.emitPeerClose(json.decodeFromNSDictionary(payloadDict))
                "onPeerError" -> events.emitPeerError(json.decodeFromNSDictionary(payloadDict))
                "onTrackerError" -> events.emitTrackerError(json.decodeFromNSDictionary(payloadDict))
                "onTrackerWarning" -> events.emitTrackerWarning(json.decodeFromNSDictionary(payloadDict))
                else -> logger.w { "Unknown message type received from WebView: $type" }
            }
        } catch (e: SerializationException) {
            logger.e { "Failed to deserialize NSDictionary payload for '$type': ${e.message}" }
        } catch (e: IllegalArgumentException) {
            logger.e { "Invalid argument in NSDictionary payload for '$type': ${e.message}" }
        } catch (e: ClassCastException) {
            logger.e { "Type cast failed for NSDictionary payload in '$type': ${e.message}" }
        }
    }
}
