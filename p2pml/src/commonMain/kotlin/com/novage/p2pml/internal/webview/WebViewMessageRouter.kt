package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
internal data class JsEventEnvelope(val type: String, val payload: JsonElement? = null)

@Serializable
internal data class EngineLogEntry(val level: String, val text: String)

@Serializable
internal data class EngineLogBatch(val entries: List<EngineLogEntry> = emptyList())

internal class WebViewMessageRouter(
    private val events: P2PEvents,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val onPageReady: (() -> Unit)? = null
) {
    companion object {
        private const val PAGE_READY_TYPE = "onWebViewLoaded"
        private const val ENGINE_LOG_TYPE = "onEngineLog"
    }

    private val logger = CoreLogger("WebViewMessageRouter")
    private val engineLogger = CoreLogger("WebEngine")

    fun handleMessage(message: String) {
        try {
            val envelope = json.decodeFromString<JsEventEnvelope>(message)

            if (envelope.type == PAGE_READY_TYPE) {
                onPageReady?.invoke()
                return
            }

            val payload = envelope.payload
            if (payload == null) {
                logger.w { "Received message of type '${envelope.type}' without a payload. Raw message: $message" }
                return
            }

            if (envelope.type == ENGINE_LOG_TYPE) {
                logEngineEntries(json.decodeFromJsonElement<EngineLogBatch>(payload))
                return
            }

            events.dispatchEvent(envelope.type, payload, json)
        } catch (e: SerializationException) {
            logger.e(e) { "Failed to parse WebView JSON message. Raw message: $message" }
        } catch (e: IllegalArgumentException) {
            logger.e(e) { "Invalid argument in WebView JSON message. Raw message: $message" }
        } catch (e: IllegalStateException) {
            logger.e(e) { "Invalid state in WebView JSON message. Raw message: $message" }
        }
    }

    private fun logEngineEntries(batch: EngineLogBatch) {
        for (entry in batch.entries) {
            when (entry.level) {
                "error" -> engineLogger.e { entry.text }
                "warn" -> engineLogger.w { entry.text }
                "info" -> engineLogger.i { entry.text }
                else -> engineLogger.d { entry.text }
            }
        }
    }
}
