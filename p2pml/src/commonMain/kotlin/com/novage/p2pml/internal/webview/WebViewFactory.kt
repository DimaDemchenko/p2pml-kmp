package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.P2PEventRegistry

internal interface WebViewFactory {
    fun createHeadlessWebView(events: P2PEventRegistry): HeadlessWebView
}
