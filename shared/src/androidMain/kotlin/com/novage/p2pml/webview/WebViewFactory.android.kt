package com.novage.p2pml.webview

import android.content.Context
import android.webkit.WebView

actual open class PlatformContext(val context: Context)

actual class PlatformWebViewFactory actual constructor(private val context: PlatformContext) {
    actual fun createWebView(): PlatformWebView = AndroidWebView(WebView(context.context))
}

class AndroidWebView(private val webView: WebView) : PlatformWebView {
    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun evaluateJavascript(script: String, callback: ((String) -> Unit)?) {
        TODO("Not yet implemented")
    }

    override fun destroy() {
        TODO("Not yet implemented")
    }
}
