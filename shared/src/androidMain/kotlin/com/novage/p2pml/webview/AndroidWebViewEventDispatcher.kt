package com.novage.p2pml.webview

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import com.novage.p2pml.events.*
import kotlinx.serialization.json.Json
import org.json.JSONObject

class AndroidWebViewEventDispatcher(
    private val eventEmitter: EventEmitter,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val onPageReady: (() -> Unit)? = null,
) {
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
                    Log.d("P2PML", "✅ WebView loaded")
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
                else -> Log.w("P2PML", "Unknown event type received: $type")
            }
        } catch (e: Exception) {
            Log.e("P2PML", "Failed to handle generic message: ${e.message}")
        }
    }

    /** Helper function to deserialize JSON payload and emit the event safely. */
    private inline fun <reified T> emitEvent(event: CoreEventMap<T>, jsonStr: String) {
        if (!eventEmitter.hasListeners(event)) return

        try {
            val data = json.decodeFromString<T>(jsonStr)
            eventEmitter.emit(event, data)
        } catch (e: Exception) {
            Log.e("P2PML", "Failed to deserialize payload for ${event.eventName}: ${e.message}")
        }
    }
}