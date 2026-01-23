package com.novage.p2pml.api.interfaces

internal interface WebViewFactory {
    fun createHeadlessWebView(
        eventEmitter: CoreEventEmitter,
        onWebViewLoaded: () -> Unit,
        onWebViewError: (String) -> Unit
    ): HeadlessWebView
}
