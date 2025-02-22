package com.novage.p2pml

import com.novage.p2pml.server.ServerModule
import io.ktor.http.encodeURLParameter
import io.ktor.util.encodeBase64
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView

actual class PlatformWebView(val webView: WKWebView)

actual class P2PMediaLoader(private val platformWebView: PlatformWebView) {
    private var serverModule: ServerModule? = null

    fun getManifestUrl(manifestUrl: String): String {
        val encodedManifest = manifestUrl.encodeBase64().encodeURLParameter()
        return "http://127.0.0.1:8080/manifest/$encodedManifest"
    }

    fun start() {
        serverModule = ServerModule(onServerStarted = { onServerStarted() })
        serverModule?.start()
    }

    private fun onServerStarted() {
        println("Server started")
        val request = NSURLRequest.requestWithURL(NSURL(string = "http://127.0.0.1:8080/static/"))
        platformWebView.webView.loadRequest(request)
    }
}
