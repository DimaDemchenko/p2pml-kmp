package com.novage.p2pml.internal.engine

import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.internal.parser.hlsPlaylistParser.Stream
import com.novage.p2pml.internal.parser.hlsPlaylistParser.UpdateStreamParams
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.webview.HeadlessWebView
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class P2PEngineManager(
    private val webView: HeadlessWebView,
    private val json: Json = Json {
        encodeDefaults = false
        explicitNulls = false
    }
) : P2PEngine {
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
        webView.destroy()
    }

    override fun initCoreEngine(coreConfig: CoreConfig, uploadUrl: String) {
        logger.i { "Initializing JS Core Engine" }
        evaluate(
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

    override fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) {
        logger.i { "Applying dynamic config" }
        evaluate("$JS_BRIDGE.applyDynamicP2PCoreConfig(${CoreConfigJsMapper.toJsExpression(dynamicCoreConfig)});")
    }

    override fun subscribeToP2PEvent(eventName: String) {
        evaluate("$JS_BRIDGE.subscribeToEvent(${json.encodeToString(eventName)});")
    }

    private fun evaluate(script: String) {
        webView.evaluateJavascript(script, null)
    }

    override fun updatePlaybackInfo(info: PlaybackInfo) {
        val jsonString = json.encodeToString(info)
        evaluate("$JS_BRIDGE.updatePlaybackInfo($jsonString);")
    }
}
