package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.api.models.ChunkDownloadedDetails
import com.novage.p2pml.api.models.ChunkUploadedDetails
import com.novage.p2pml.api.models.DownloadSource
import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class JsEventEnvelope(val type: String, val payload: JsonElement? = null)

internal class WebViewMessageRouter(
    private val events: P2PEvents,
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

            events.dispatchEvent(envelope.type, payload, json)
        } catch (e: SerializationException) {
            logger.e { "Failed to parse WebView JSON message: ${e.message}. Raw message: $message" }
        } catch (e: IllegalArgumentException) {
            logger.e { "Invalid argument in WebView JSON message: ${e.message}. Raw message: $message" }
        } catch (e: IllegalStateException) {
            logger.e { "Invalid state in WebView JSON message: ${e.message}. Raw message: $message" }
        }
    }

    private fun handleChunkDownloaded(payload: JsonElement?) {
        val obj = payload as? JsonObject ?: return
        val bytesLength = obj["bytesLength"]?.jsonPrimitive?.intOrNull ?: return
        val downloadSource = obj["downloadSource"]?.jsonPrimitive?.contentOrNull ?: return
        val peerId = obj["peerId"]?.jsonPrimitive?.contentOrNull

        val source = DownloadSource.fromValue(downloadSource)
        events.emitChunkDownloaded(ChunkDownloadedDetails(bytesLength, source, peerId))
    }

    private fun handleChunkUploaded(payload: JsonElement?) {
        val obj = payload as? JsonObject ?: return
        val bytesLength = obj["bytesLength"]?.jsonPrimitive?.intOrNull ?: return
        val peerId = obj["peerId"]?.jsonPrimitive?.contentOrNull ?: return

        events.emitChunkUploaded(ChunkUploadedDetails(bytesLength, peerId))
    }
}
