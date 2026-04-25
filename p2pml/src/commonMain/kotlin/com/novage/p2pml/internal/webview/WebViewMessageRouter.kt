package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.P2PEventRegistry
import com.novage.p2pml.api.models.ChunkDownloadedDetails
import com.novage.p2pml.api.models.ChunkUploadedDetails
import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class JsEventEnvelope(val type: String, val payload: JsonElement? = null)

internal class WebViewMessageRouter(
    private val events: P2PEventRegistry,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val onPageReady: (() -> Unit)? = null
) {
    private val logger = CoreLogger("WebViewMessageRouter")

    fun handleMessage(message: String) {
        try {
            val envelope = json.decodeFromString<JsEventEnvelope>(message)

            when (envelope.type) {
                "onWebViewLoaded" -> {
                    onPageReady?.invoke()
                    return
                }

                "onChunkDownloaded" -> {
                    handleChunkDownloaded(envelope.payload)
                    return
                }

                "onChunkUploaded" -> {
                    handleChunkUploaded(envelope.payload)
                    return
                }
            }

            val payload = envelope.payload
            if (payload == null) {
                logger.w { "Received message of type '${envelope.type}' without a payload. Raw message: $message" }
                return
            }

            events.dispatchEventFromJsonElement(envelope.type, payload, json)
        } catch (e: SerializationException) {
            logger.e { "Failed to parse WebView JSON message: ${e.message}. Raw message: $message" }
        } catch (e: IllegalArgumentException) {
            logger.e { "Invalid argument in WebView JSON message: ${e.message}. Raw message: $message" }
        }
    }

    private fun handleChunkDownloaded(payload: JsonElement?) {
        if (payload == null) return

        val obj = payload.jsonObject
        val bytesLength = obj["bytesLength"]?.jsonPrimitive?.intOrNull ?: return
        val downloadSource = obj["downloadSource"]?.jsonPrimitive?.content ?: return
        val peerId = obj["peerId"]?.jsonPrimitive?.content

        events.emitChunkDownloaded(ChunkDownloadedDetails(bytesLength, downloadSource, peerId))
    }

    private fun handleChunkUploaded(payload: JsonElement?) {
        if (payload == null) return

        val obj = payload.jsonObject
        val bytesLength = obj["bytesLength"]?.jsonPrimitive?.intOrNull ?: return
        val peerId = obj["peerId"]?.jsonPrimitive?.content ?: return

        events.emitChunkUploaded(ChunkUploadedDetails(bytesLength, peerId))
    }
}
