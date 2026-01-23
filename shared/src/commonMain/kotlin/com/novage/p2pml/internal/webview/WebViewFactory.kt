package com.novage.p2pml.internal.webview

import com.novage.p2pml.internal.webview.HeadlessWebView
import com.novage.p2pml.internal.events.CoreEventEmitter

internal interface WebViewFactory {
    fun createHeadlessWebView(
        eventEmitter: CoreEventEmitter,
        onWebViewLoaded: () -> Unit,
        onWebViewError: (String) -> Unit
    ): HeadlessWebView
}
