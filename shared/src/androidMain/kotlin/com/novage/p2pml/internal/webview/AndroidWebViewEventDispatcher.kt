package com.novage.p2pml.internal.webview

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.novage.p2pml.api.models.ChunkDownloadedDetails
import com.novage.p2pml.api.models.ChunkUploadedDetails
import com.novage.p2pml.internal.events.CoreEventEmitter
import com.novage.p2pml.internal.events.CoreEventMap
import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.json.JSONException
import org.json.JSONObject

internal class AndroidWebViewEventDispatcher(
    private val eventEmitter: CoreEventEmitter,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val onPageReady: (() -> Unit)? = null
) {
    private val logger = CoreLogger("AndroidWebViewEventDispatcher")
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onChunkDownloaded(bytesLength: Int, downloadSource: String, peerId: String?) {
        if (!eventEmitter.hasListeners(CoreEventMap.OnChunkDownloaded)) return

        mainHandler.post {
            val details = ChunkDownloadedDetails(bytesLength, downloadSource, peerId)
            eventEmitter.emit(CoreEventMap.OnChunkDownloaded, details)
        }
    }

    @JavascriptInterface
    fun onChunkUploaded(bytesLength: Int, peerId: String) {
        if (!eventEmitter.hasListeners(CoreEventMap.OnChunkUploaded)) return

        mainHandler.post {
            val details = ChunkUploadedDetails(bytesLength, peerId)
            eventEmitter.emit(CoreEventMap.OnChunkUploaded, details)
        }
    }

    @JavascriptInterface
    fun postMessage(message: String) {
        mainHandler.post {
            handleGenericMessage(message)
        }
    }

    private fun handleGenericMessage(message: String) {
        try {
            val root = JSONObject(message)
            val type = root.optString("type")

            val payloadStr = root.opt("payload")?.toString() ?: "{}"

            when (type) {
                "onWebViewLoaded" -> {
                    onPageReady?.invoke()
                }

                "onSegmentLoaded" -> emitEvent(CoreEventMap.OnSegmentLoaded, payloadStr)
                "onSegmentStart" -> emitEvent(CoreEventMap.OnSegmentStart, payloadStr)
                "onSegmentError" -> emitEvent(CoreEventMap.OnSegmentError, payloadStr)
                "onSegmentAbort" -> emitEvent(CoreEventMap.OnSegmentAbort, payloadStr)
                "onPeerConnect" -> emitEvent(CoreEventMap.OnPeerConnect, payloadStr)
                "onPeerClose" -> emitEvent(CoreEventMap.OnPeerClose, payloadStr)
                "onPeerError" -> emitEvent(CoreEventMap.OnPeerError, payloadStr)
                "onTrackerError" -> emitEvent(CoreEventMap.OnTrackerError, payloadStr)
                "onTrackerWarning" -> emitEvent(CoreEventMap.OnTrackerWarning, payloadStr)
                else -> logger.w { "Unknown message type received from WebView: $type" }
            }
        } catch (e: JSONException) {
            logger.e { "Failed to parse message JSON: ${e.message}" }
        }
    }

    /** Helper function to deserialize JSON payload and emit the event safely. */
    private inline fun <reified T> emitEvent(event: CoreEventMap<T>, jsonStr: String) {
        if (!eventEmitter.hasListeners(event)) return

        try {
            val data = json.decodeFromString<T>(jsonStr)
            eventEmitter.emit(event, data)
        } catch (e: SerializationException) {
            logger.e { "Failed to deserialize payload for ${event.eventName}: ${e.message}" }
        } catch (e: IllegalArgumentException) {
            logger.e { "Invalid arguments for ${event.eventName}: ${e.message}" }
        }
    }
}
