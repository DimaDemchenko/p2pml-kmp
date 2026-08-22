package com.novage.p2pml.internal.engine

import com.novage.p2pml.api.config.CoreConfig
import com.novage.p2pml.api.config.DynamicCoreConfig
import com.novage.p2pml.internal.parser.hls.Stream
import com.novage.p2pml.internal.parser.hls.UpdateStreamParams
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.webview.HeadlessWebView
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire shape the bridge page destructures in `updatePlaybackInfo`. Keep the field names in sync. */
@Serializable
private data class EnginePlaybackPayload(val currentPlayPosition: Double, val currentPlaybackSpeed: Float)

internal val engineBridgeJson = Json {
    encodeDefaults = false
    explicitNulls = false
}

internal class P2PEngineManager(private val webView: HeadlessWebView, private val json: Json = engineBridgeJson) :
    P2PEngine {
    private val logger = CoreLogger("P2PEngineManager")

    companion object {
        private const val JS_BRIDGE = "window.p2p"
    }

    override suspend fun loadUrlAndWait(url: String) {
        logger.d { "Loading Web Engine URL: $url" }
        webView.loadUrlAndWait(url)
    }

    override fun destroy() {
        logger.d { "Destroying P2PEngineManager..." }
        evaluate("$JS_BRIDGE.destroyP2PEngine();")
        webView.destroy()
    }

    override suspend fun initCoreEngineAndWait(coreConfig: CoreConfig, uploadUrl: String) {
        logger.i { "Initializing JS Core Engine" }
        webView.initCoreAndWait(
            "$JS_BRIDGE.initP2P(${CoreConfigJsMapper.toJsExpression(coreConfig)}, " +
                "${json.encodeToString(uploadUrl)});"
        )
    }

    override fun requestSegmentBytes(segmentUrl: String) {
        logger.d { "Requesting segment via P2P Engine: $segmentUrl" }
        evaluate("$JS_BRIDGE.processSegmentRequest(${json.encodeToString(segmentUrl)});")
    }

    override fun sendStream(stream: UpdateStreamParams) {
        val streamJson = json.encodeToString(stream)
        evaluate("$JS_BRIDGE.parseStream($streamJson);")
    }

    override fun sendAllStreams(streams: List<Stream>) {
        val streamsJson = json.encodeToString(streams)
        evaluate("$JS_BRIDGE.parseAllStreams($streamsJson);")
    }

    override fun unsubscribeFromP2PEvent(eventName: String) {
        evaluate("$JS_BRIDGE.unsubscribeFromEvent(${json.encodeToString(eventName)});")
    }

    override fun setManifestUrl(manifestUrl: String) {
        logger.d { "Setting manifest URL in P2P Engine: $manifestUrl" }
        evaluate("$JS_BRIDGE.setManifestUrl(${json.encodeToString(manifestUrl)});")
    }

    override fun applyDynamicConfig(config: DynamicCoreConfig) {
        logger.i { "Applying dynamic config" }
        evaluate("$JS_BRIDGE.applyDynamicP2PCoreConfig(${CoreConfigJsMapper.toJsExpression(config)});")
    }

    override fun subscribeToP2PEvent(eventName: String) {
        evaluate("$JS_BRIDGE.subscribeToEvent(${json.encodeToString(eventName)});")
    }

    private fun evaluate(script: String) {
        webView.evaluateJavascript(script)
    }

    override fun updatePlaybackInfo(positionSec: Double, speed: Float) {
        val jsonString = json.encodeToString(EnginePlaybackPayload(positionSec, speed))
        evaluate("$JS_BRIDGE.updatePlaybackInfo($jsonString);")
    }
}
