package com.novage.p2pml.engine

import com.novage.p2pml.domain.interfaces.P2PEngine
import com.novage.p2pml.domain.interfaces.PlaybackProvider
import com.novage.p2pml.webview.HeadlessWebView
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

class P2PEngineManager(
    private val webView: HeadlessWebView,
    private val playbackProvider: PlaybackProvider
) : P2PEngine {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var playbackInfoJob: Job? = null

    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun destroy() {
        scope.cancel()
        webView.destroy()
    }

    override fun initCoreEngine(coreConfigJson: String, uploadUrl: String) {
        evaluate("window.p2p.initP2P('$coreConfigJson', '$uploadUrl');")
    }

    override fun requestSegmentBytes(segmentUrl: String) {
        startPlaybackInfoUpdate()
        evaluate("window.p2p.processSegmentRequest('$segmentUrl');")
    }

    override fun sendStream(streamJson: String) {
        evaluate("window.p2p.parseStream('$streamJson');")
    }

    override fun sendAllStreams(streamsJson: String) {
        evaluate("window.p2p.parseAllStreams('$streamsJson');")
    }

    override fun setManifestUrl(manifestUrl: String) {
        evaluate("window.p2p.setManifestUrl('$manifestUrl');")
    }

    override fun applyDynamicConfig(dynamicConfigJson: String) {
        evaluate("window.p2p.applyDynamicP2PCoreConfig('$dynamicConfigJson');")
    }

    override fun subscribeToP2PEvent(eventName: String) {
        evaluate("window.p2p.subscribeToEvent('$eventName');")
    }

    override fun unsubscribeFromP2PEvent(eventName: String) {
        evaluate("window.p2p.unsubscribeFromEvent('$eventName');")
    }

    private fun evaluate(script: String) {
        webView.evaluateJavascript("javascript:$script", null)
    }

    private fun startPlaybackInfoUpdate() {
        if (playbackInfoJob?.isActive == true) return

        playbackInfoJob = scope.launch {
            while (isActive) {
                try {
                    val info = playbackProvider.getPlaybackPositionAndSpeed()
                    val json = Json.encodeToString(info)

                    evaluate("window.p2p.updatePlaybackInfo('$json');")

                    delay(1000)
                } catch (e: Exception) {
                    println("Heartbeat error: ${e.message}")
                }
            }
        }
    }
}