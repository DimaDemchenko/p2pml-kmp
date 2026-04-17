package com.novage.p2pml.internal.webview

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.novage.p2pml.api.events.P2PEventRegistry
import com.novage.p2pml.api.models.ChunkDownloadedDetails
import com.novage.p2pml.api.models.ChunkUploadedDetails
import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
internal data class JsEventEnvelope(val type: String, val payload: JsonElement? = null)

internal class AndroidWebViewEventDispatcher(
    private val events: P2PEventRegistry,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val onPageReady: (() -> Unit)? = null
) {
    private val logger = CoreLogger("AndroidWebViewEventDispatcher")
    private val mainHandler = Handler(Looper.getMainLooper())

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
        try {
            val envelope = json.decodeFromString<JsEventEnvelope>(message)

            if (envelope.type == "onWebViewLoaded") {
                mainHandler.post { onPageReady?.invoke() }
                return
            }

            val payload = envelope.payload ?: return

            when (envelope.type) {
                "onSegmentLoaded" -> events.emitSegmentLoaded(json.decodeFromJsonElement(payload))
                "onSegmentStart" -> events.emitSegmentStart(json.decodeFromJsonElement(payload))
                "onSegmentError" -> events.emitSegmentError(json.decodeFromJsonElement(payload))
                "onSegmentAbort" -> events.emitSegmentAbort(json.decodeFromJsonElement(payload))
                "onPeerConnect" -> events.emitPeerConnect(json.decodeFromJsonElement(payload))
                "onPeerClose" -> events.emitPeerClose(json.decodeFromJsonElement(payload))
                "onPeerError" -> events.emitPeerError(json.decodeFromJsonElement(payload))
                "onTrackerError" -> events.emitTrackerError(json.decodeFromJsonElement(payload))
                "onTrackerWarning" -> events.emitTrackerWarning(json.decodeFromJsonElement(payload))
                else -> logger.w { "Unknown message type received from WebView: ${envelope.type}" }
            }
        } catch (e: SerializationException) {
            logger.e { "Failed to deserialize JSON payload: ${e.message}" }
        } catch (e: IllegalArgumentException) {
            logger.e { "Invalid argument in JSON payload: ${e.message}" }
        }
    }
}
