package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.events.P2PEvents

internal interface WebViewFactory {
    fun createHeadlessWebView(
        events: P2PEvents,
        onFatalError: (P2PMediaLoaderException) -> Unit
    ): HeadlessWebView
}
