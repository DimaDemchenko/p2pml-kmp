package com.novage.p2pml.webview

import com.novage.p2pml.eventEmitter.EventEmitter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRect
import platform.Foundation.setValue
import platform.WebKit.WKPreferences
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

actual class PlatformContext

actual class PlatformWebViewFactory actual constructor(private val context: PlatformContext) {
    @OptIn(ExperimentalForeignApi::class)
    actual fun createWebView(
        eventEmitter: EventEmitter,
        onWebviewLoaded: () -> Unit,
    ): PlatformWebView {
        val configuration = WKWebViewConfiguration()
        val userController = configuration.userContentController

        val scriptMessageHandler = WebViewEventDispatcher(eventEmitter) { onWebviewLoaded() }
        userController.addScriptMessageHandler(scriptMessageHandler, "p2pml")

        val preferences = WKPreferences()
        preferences.setValue(true, forKey = "developerExtrasEnabled")
        configuration.preferences = preferences

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

        val webView = WKWebView(frame = frame, configuration = configuration)

        // Make webView hidden but functional
        webView.hidden = true
        webView.userInteractionEnabled = false
        webView.inspectable = true

        return IOSWebView(webView)
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
