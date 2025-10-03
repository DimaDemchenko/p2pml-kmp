package com.novage.p2pml.webview

import com.novage.p2pml.providers.PlaybackProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import platform.WebKit.WKWebView

actual class WebViewManagerImpl(
    private val webView: WKWebView,
    private val playbackProvider: PlaybackProvider,
    private val coroutine: CoroutineScope,
) : WebViewManager {

    private var playbackInfoJob: Job? = null

    override fun loadUrl(url: String) {
        val nsUrl = platform.Foundation.NSURL(string = url)
        val request = platform.Foundation.NSURLRequest(nsUrl)

        webView.loadRequest(request)
    }

    private fun startPlaybackInfoUpdate() {
        if (playbackInfoJob !== null) return

        playbackInfoJob =
            coroutine.launch {
                while (isActive) {
                    try {
                        //                        if (engineStateManager.isEngineDisabled()) {
                        //                            playbackInfoJob?.cancel()
                        //                            playbackInfoJob = null
                        //                            break
                        //                        }

                        val currentPlaybackInfo = playbackProvider.getPlaybackPositionAndSpeed()
                        val playbackInfoJson = Json.encodeToString(currentPlaybackInfo)

                        sendPlaybackInfo(playbackInfoJson)

                        delay(1000)
                    } catch (e: Exception) {
                        println("Playback info update failed: ${e.message}")
                        // Logger.d(TAG, "Playback info update failed: ${e.message}")
                    }
                }
            }
    }

    override fun applyDynamicConfig(config: String) {
        TODO("Not yet implemented")
    }

    private suspend fun sendPlaybackInfo(playbackInfoJson: String) {
        withContext(Dispatchers.Main) {
            webView.evaluateJavaScript(
                "javascript:window.p2p.updatePlaybackInfo('$playbackInfoJson');",
                null,
            )
        }
    }

    override suspend fun setManifestUrl(manifestUrl: String) {
        withContext(Dispatchers.Main) {
            webView.evaluateJavaScript(
                "javascript:window.p2p.setManifestUrl('$manifestUrl');",
                null,
            )
        }
    }

    override suspend fun sendAllStreams(streamsJson: String) {
        withContext(Dispatchers.Main) {
            webView.evaluateJavaScript(
                "javascript:window.p2p.parseAllStreams('$streamsJson');",
                null,
            )
        }
    }

    override suspend fun sendStream(streamJson: String) {
        withContext(Dispatchers.Main) {
            webView.evaluateJavaScript("javascript:window.p2p.parseStream('$streamJson');", null)
        }
    }

    override suspend fun requestSegmentBytes(segmentUrl: String) {
        withContext(Dispatchers.Main) {
            startPlaybackInfoUpdate()

            webView.evaluateJavaScript(
                "javascript:window.p2p.processSegmentRequest('$segmentUrl');",
                null,
            )
        }
    }

    override suspend fun initCoreEngine(coreConfigJson: String) {
        withContext(Dispatchers.Main) {
            webView.evaluateJavaScript("javascript:window.p2p.initP2P('$coreConfigJson');", null)
        }
    }
}
