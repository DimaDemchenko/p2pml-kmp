package com.novage.p2pml.webview

import com.novage.p2pml.eventEmitter.*
import com.novage.p2pml.utils.decodeFromNSDictionary
import kotlinx.serialization.json.Json
import platform.Foundation.*
import platform.WebKit.*
import platform.darwin.NSObject

class WebViewEventDispatcher(
    private val eventEmitter: EventEmitter,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val onPageReady: (() -> Unit)? = null,
) : NSObject(), WKScriptMessageHandlerProtocol {

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val body = didReceiveScriptMessage.body as? NSDictionary ?: return
        val type = body.objectForKey("type") as? String ?: return

        when (type) {
            "onWebViewLoaded" -> {
                println("✅ WebView reports: page ready")
                onPageReady?.invoke()
            }
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

        eventEmitter.emit(
            CoreEventMap.OnChunkDownloaded,
            ChunkDownloadedDetails(bytesLength, downloadSource, peerId),
        )
    }

    private fun handleChunkUploaded(body: NSDictionary) {
        val payload = body.objectForKey("payload") as? NSDictionary ?: return
        val bytesLength = (payload.objectForKey("bytesLength") as? Number)?.toInt() ?: return
        val peerId = payload.objectForKey("peerId") as? String ?: return

        eventEmitter.emit(CoreEventMap.OnChunkUploaded, ChunkUploadedDetails(bytesLength, peerId))
    }

    private fun handleComplexEvent(type: String, body: NSDictionary) {
        val payloadDict = body.objectForKey("payload") as? NSDictionary ?: return

        try {
            when (type) {
                "onSegmentLoaded" -> {
                    val details = json.decodeFromNSDictionary<SegmentLoadDetails>(payloadDict)
                    eventEmitter.emit(CoreEventMap.OnSegmentLoaded, details)
                }
                "onSegmentStart" -> {
                    val details = json.decodeFromNSDictionary<SegmentStartDetails>(payloadDict)
                    eventEmitter.emit(CoreEventMap.OnSegmentStart, details)
                }
                "onSegmentAbort" -> {
                    val details = json.decodeFromNSDictionary<SegmentAbortDetails>(payloadDict)
                    eventEmitter.emit(CoreEventMap.OnSegmentAbort, details)
                }
                "onSegmentError" -> {
                    val details = json.decodeFromNSDictionary<SegmentErrorDetails>(payloadDict)
                    eventEmitter.emit(CoreEventMap.OnSegmentError, details)
                }
                "onPeerConnect" -> {
                    val details = json.decodeFromNSDictionary<PeerDetails>(payloadDict)
                    eventEmitter.emit(CoreEventMap.OnPeerConnect, details)
                }
                "onPeerClose" -> {
                    val details = json.decodeFromNSDictionary<PeerDetails>(payloadDict)
                    eventEmitter.emit(CoreEventMap.OnPeerClose, details)
                }
                "onPeerError" -> {
                    val details = json.decodeFromNSDictionary<PeerErrorDetails>(payloadDict)
                    eventEmitter.emit(CoreEventMap.OnPeerError, details)
                }
                "onTrackerError" -> {
                    val details = json.decodeFromNSDictionary<TrackerErrorDetails>(payloadDict)
                    eventEmitter.emit(CoreEventMap.OnTrackerError, details)
                }
                "onTrackerWarning" -> {
                    val details = json.decodeFromNSDictionary<TrackerWarningDetails>(payloadDict)
                    eventEmitter.emit(CoreEventMap.OnTrackerWarning, details)
                }
            }
        } catch (t: Throwable) {
            println("Failed to handle '$type': ${t.message}")
        }
    }
}
