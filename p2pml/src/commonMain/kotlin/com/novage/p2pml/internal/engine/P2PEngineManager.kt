package com.novage.p2pml.internal.engine

import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.webview.HeadlessWebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val PLAYBACK_UPDATE_INTERVAL_MS = 1000L

internal class P2PEngineManager(private val webView: HeadlessWebView, private val playbackProvider: PlaybackProvider) :
    P2PEngine {

    private val logger = CoreLogger("P2PEngineManager")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var playbackInfoJob: Job? = null

    override fun loadUrl(url: String) {
        logger.d { "Loading Web Engine URL: $url" }
        webView.loadUrl(url)
    }

    override fun destroy() {
        logger.d { "Destroying P2PEngineManager..." }

        playbackInfoJob?.cancel()
        scope.cancel()
        webView.destroy()
    }

    override fun initCoreEngine(coreConfig: String, uploadUrl: String) {
        logger.i { "Initializing JS Core Engine" }
        evaluate("window.p2p.initP2P('$coreConfig', '$uploadUrl');")
    }

    override fun requestSegmentBytes(segmentUrl: String) {
        logger.d { "Requesting segment via P2P Engine: $segmentUrl" }

        startPlaybackInfoUpdate()
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
        evaluate("window.p2p.applyDynamicP2PCoreConfig('$dynamicCoreConfig');")
    }

    override fun subscribeToP2PEvent(eventName: String) {
        evaluate("window.p2p.subscribeToEvent('$eventName');")
    }

    private fun evaluate(script: String) {
        webView.evaluateJavascript("javascript:$script", null)
    }

    private fun startPlaybackInfoUpdate() {
        if (playbackInfoJob?.isActive == true) return

        logger.d { "Starting playback info update loop." }

        playbackInfoJob = scope.launch {
            while (isActive) {
                try {
                    val info = playbackProvider.getPlaybackPositionAndSpeed()
                    val json = Json.encodeToString(info)

                    evaluate("window.p2p.updatePlaybackInfo('$json');")

                    delay(PLAYBACK_UPDATE_INTERVAL_MS)
                } catch (e: SerializationException) {
                    logger.e { "Failed to serialize playback info: ${e.message}" }
                }
            }
        }
    }
}
