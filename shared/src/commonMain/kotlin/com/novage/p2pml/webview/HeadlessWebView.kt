package com.novage.p2pml.webview

import com.novage.p2pml.eventEmitter.EventEmitter

interface WebViewFactory {
    fun createHeadlessWebView(eventEmitter: EventEmitter, onWebviewLoaded: () -> Unit): HeadlessWebView
}

interface HeadlessWebView {
    fun loadUrl(url: String)

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?)

    fun destroy()
}
