package com.novage.p2pml.webview

import com.novage.p2pml.domain.interfaces.CoreEventEmitter

interface WebViewFactory {
    fun createHeadlessWebView(
        eventEmitter: CoreEventEmitter,
        onWebViewLoaded: () -> Unit,
        onWebViewError: (String) -> Unit
    ): HeadlessWebView
}

interface HeadlessWebView {
    fun loadUrl(url: String)

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?)

    fun destroy()
}
