package com.novage.p2pml.webview

import com.novage.p2pml.eventEmitter.EventEmitter

expect class PlatformContext

expect class PlatformWebViewFactory(context: PlatformContext) {
    fun createWebView(eventEmitter: EventEmitter, onWebviewLoaded: () -> Unit): PlatformWebView
}

interface PlatformWebView {
    fun loadUrl(url: String)

    fun evaluateJavascript(script: String, callback: ((String) -> Unit)?)

    fun destroy()
}
