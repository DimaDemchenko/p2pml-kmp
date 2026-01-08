package com.novage.p2pml.webview

import com.novage.p2pml.domain.models.ChunkDownloadedDetails
import com.novage.p2pml.domain.models.ChunkUploadedDetails
import com.novage.p2pml.domain.models.CoreEventMap
import com.novage.p2pml.domain.models.PeerDetails
import com.novage.p2pml.domain.models.PeerErrorDetails
import com.novage.p2pml.domain.models.SegmentAbortDetails
import com.novage.p2pml.domain.models.SegmentErrorDetails
import com.novage.p2pml.domain.models.SegmentLoadDetails
import com.novage.p2pml.domain.models.SegmentStartDetails
import com.novage.p2pml.domain.models.TrackerErrorDetails
import com.novage.p2pml.domain.models.TrackerWarningDetails
import com.novage.p2pml.events.EventEmitter
import com.novage.p2pml.utils.decodeFromNSDictionary
import kotlinx.serialization.json.Json
import platform.Foundation.NSDictionary
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.darwin.NSObject

class IosWebViewEventDispatcher(
    private val eventEmitter: EventEmitter,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val onPageReady: (() -> Unit)? = null
) : NSObject(),
    WKScriptMessageHandlerProtocol {

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
        if (!eventEmitter.hasListeners(CoreEventMap.OnChunkDownloaded)) return

        val payload = body.objectForKey("payload") as? NSDictionary ?: return
        val bytesLength = (payload.objectForKey("bytesLength") as? Number)?.toInt() ?: return
        val downloadSource = payload.objectForKey("downloadSource") as? String ?: return
        val peerId = payload.objectForKey("peerId") as? String

        eventEmitter.emit(
            CoreEventMap.OnChunkDownloaded,
            ChunkDownloadedDetails(bytesLength, downloadSource, peerId)
        )
    }

    private fun handleChunkUploaded(body: NSDictionary) {
        if (!eventEmitter.hasListeners(CoreEventMap.OnChunkUploaded)) return

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
