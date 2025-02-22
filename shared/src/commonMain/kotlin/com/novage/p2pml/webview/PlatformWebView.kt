package com.novage.p2pml.webview

expect class PlatformContext

expect class PlatformWebViewFactory(context: PlatformContext) {
    fun createWebView(): PlatformWebView
}

interface PlatformWebView {
    fun loadUrl(url: String)

    fun evaluateJavascript(script: String, callback: ((String) -> Unit)?)

    fun addJavaScriptInterface(`object`: Any, name: String)

    fun removeJavascriptInterface(name: String)

    fun destroy()
}
