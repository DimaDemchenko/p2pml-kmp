package com.novage.p2pml.internal.engine

import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.webview.HeadlessWebView

internal class P2PEngineManager(private val webView: HeadlessWebView) : P2PEngine {
    private val logger = CoreLogger("P2PEngineManager")

    override fun loadUrl(url: String) {
        logger.d { "Loading Web Engine URL: $url" }
        webView.loadUrl(url)
    }

    override fun destroy() {
        logger.d { "Destroying P2PEngineManager..." }

        webView.destroy()
    }

    override fun initCoreEngine(coreConfig: String, uploadUrl: String) {
        logger.i { "Initializing JS Core Engine" }
        evaluate("window.p2p.initP2P($coreConfig, '$uploadUrl');")
    }

    override fun requestSegmentBytes(segmentUrl: String) {
        logger.d { "Requesting segment via P2P Engine: $segmentUrl" }

        evaluate("window.p2p.processSegmentRequest('$segmentUrl');")
    }

    override fun sendStream(streamJson: String) {
        evaluate("window.p2p.parseStream('$streamJson');")
    }

    override fun sendAllStreams(streamsJson: String) {
        evaluate("window.p2p.parseAllStreams('$streamsJson');")
    }

    override fun unsubscribeFromP2PEvent(eventName: String) {
        evaluate("window.p2p.unsubscribeFromEvent('$eventName');")
    }

    override fun setManifestUrl(manifestUrl: String) {
        logger.d { "Setting manifest URL in P2P Engine: $manifestUrl" }
        evaluate("window.p2p.setManifestUrl('$manifestUrl');")
    }

    override fun applyDynamicConfig(dynamicCoreConfig: String) {
        logger.i { "Applying dynamic config: $dynamicCoreConfig" }
        evaluate("window.p2p.applyDynamicP2PCoreConfig($dynamicCoreConfig);")
    }

    override fun subscribeToP2PEvent(eventName: String) {
        evaluate("window.p2p.subscribeToEvent('$eventName');")
    }

    private fun evaluate(script: String) {
        webView.evaluateJavascript("javascript:$script", null)
    }

    override fun updatePlaybackInfo(json: String) {
        evaluate("window.p2p.updatePlaybackInfo('$json');")
    }
}
