package com.novage.p2pml.domain.interfaces

interface WebViewFactory {
    fun createHeadlessWebView(
        eventEmitter: CoreEventEmitter,
        onWebViewLoaded: () -> Unit,
        onWebViewError: (String) -> Unit
    ): HeadlessWebView
}
