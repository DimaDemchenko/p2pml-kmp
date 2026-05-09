package com.novage.p2pml.internal.engine

import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.internal.parser.hlsPlaylistParser.Stream
import com.novage.p2pml.internal.parser.hlsPlaylistParser.UpdateStreamParams
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.webview.HeadlessWebView
import kotlinx.serialization.encodeToString

internal class P2PEngineManager(
    private val webView: HeadlessWebView,
    private val json: Json = Json { encodeDefaults = true }
) : P2PEngine {
    private val logger = CoreLogger("P2PEngineManager")

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
        evaluate("window.p2p.initP2P(${CoreConfigJsMapper.toJsExpression(coreConfig)}, '$uploadUrl');")
    }

    override fun requestSegmentBytes(segmentUrl: String) {
        logger.d { "Requesting segment via P2P Engine: $segmentUrl" }
        evaluate("window.p2p.processSegmentRequest('$segmentUrl');")
    }

    override fun sendStream(stream: UpdateStreamParams) {
        val streamJson = json.encodeToString(stream)
        evaluate("window.p2p.parseStream('$streamJson');")
    }

    override fun sendAllStreams(streams: List<Stream>) {
        val streamsJson = json.encodeToString(streams)
        evaluate("window.p2p.parseAllStreams('$streamsJson');")
    }

    override fun unsubscribeFromP2PEvent(eventName: String) {
        evaluate("window.p2p.unsubscribeFromEvent('$eventName');")
    }

    override fun setManifestUrl(manifestUrl: String) {
        logger.d { "Setting manifest URL in P2P Engine: $manifestUrl" }
        evaluate("window.p2p.setManifestUrl('$manifestUrl');")
    }

    override fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) {
        logger.i { "Applying dynamic config" }
        evaluate("window.p2p.applyDynamicP2PCoreConfig(${CoreConfigJsMapper.toJsExpression(dynamicCoreConfig)});")
    }

    override fun subscribeToP2PEvent(eventName: String) {
        evaluate("window.p2p.subscribeToEvent('$eventName');")
    }

    private fun evaluate(script: String) {
        webView.evaluateJavascript("javascript:$script", null)
    }

    override fun updatePlaybackInfo(info: PlaybackInfo) {
        val jsonString = json.encodeToString(info)
        evaluate("window.p2p.updatePlaybackInfo('$jsonString');")
    }
}
