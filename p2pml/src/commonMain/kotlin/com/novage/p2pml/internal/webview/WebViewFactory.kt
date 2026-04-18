package com.novage.p2pml.internal.webview

import com.novage.p2pml.P2PMediaLoaderErrorType
import com.novage.p2pml.api.events.P2PEventRegistry

internal interface WebViewFactory {
    fun createHeadlessWebView(
        events: P2PEventRegistry,
        onWebViewLoaded: () -> Unit,
        onWebViewError: (P2PMediaLoaderErrorType, String) -> Unit
    ): HeadlessWebView
}
