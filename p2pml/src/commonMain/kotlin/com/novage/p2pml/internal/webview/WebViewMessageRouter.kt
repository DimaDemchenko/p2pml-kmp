package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

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

            if (envelope.type == "onWebViewLoaded") {
                onPageReady?.invoke()
                return
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
}
