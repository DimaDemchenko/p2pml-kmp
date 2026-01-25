package com.novage.p2pml.internal.webview

import com.novage.p2pml.P2PMediaLoaderErrorType
import com.novage.p2pml.internal.events.CoreEventEmitter

internal interface WebViewFactory {
    fun createHeadlessWebView(
        eventEmitter: CoreEventEmitter,
        onWebViewLoaded: () -> Unit,
        onWebViewError: (P2PMediaLoaderErrorType, String) -> Unit
    ): HeadlessWebView
}
