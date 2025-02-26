package com.novage.p2pml

import com.novage.p2pml.providers.DefaultPlaybackProvider
import com.novage.p2pml.server.ServerModule
import io.ktor.http.encodeURLParameter
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView

actual class PlatformWebView(val webView: WKWebView)

actual class P2PMediaLoader(private var platformWebView: PlatformWebView?) {
    private var serverModule: ServerModule? = null
    private val defaultPlaybackProvider =
        DefaultPlaybackProvider(getPlaybackInfo = { PlaybackInfo(0.0, 0.0F) })

    fun getManifestUrl(manifestUrl: String): String {
        val encodedManifest = manifestUrl.encodeURLParameter()
        val newUrl = "http://127.0.0.1:8080/manifest/$encodedManifest"
        println("Manifest URL: $newUrl")
        return newUrl
    }

    fun start() {
        serverModule =
            ServerModule(
                playbackProvider = defaultPlaybackProvider,
                onServerStarted = { onServerStarted() },
            )
        serverModule?.start()
    }

    private fun onServerStarted() {
        println("Server started")
        val request = NSURLRequest.requestWithURL(NSURL(string = "http://127.0.0.1:8080/static/"))
        // platformWebView.webView.loadRequest(request)
    }
}
