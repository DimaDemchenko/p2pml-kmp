package com.novage.p2pml.webview

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRect
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

actual class PlatformContext

actual class PlatformWebViewFactory actual constructor(private val context: PlatformContext) {

    @OptIn(ExperimentalForeignApi::class)
    actual fun createWebView(): PlatformWebView {
        val configuration = WKWebViewConfiguration()

        val frame = memScoped {
            alloc<CGRect>()
                .apply {
                    origin.x = 0.0
                    origin.y = 0.0
                    size.width = 0.0
                    size.height = 0.0
                }
                .readValue()
        }
        return IOSWebView(WKWebView(frame = frame, configuration = configuration))
    }
}

class IOSWebView(private val webView: WKWebView) : PlatformWebView {
    override fun loadUrl(url: String) {
        val nsUrl = platform.Foundation.NSURL(string = url)
        val request = platform.Foundation.NSURLRequest(nsUrl)

        webView.loadRequest(request)
    }

    override fun evaluateJavascript(script: String, callback: ((String) -> Unit)?) {
        webView.evaluateJavaScript(script, null)
    }

    override fun destroy() {
        // TODO: Implement cleanup.
        throw NotImplementedError("Not yet implemented")
    }
}
