package com.novage.p2pml.internal.webview

/**
 * Platform-agnostic interface for a headless (non-UI) WebView.
 *
 * All methods must be safe to call from any thread.
 * Platform implementations are responsible for dispatching to the UI thread internally.
 */
internal interface HeadlessWebView {
    suspend fun loadUrlAndWait(url: String)

    fun evaluateJavascript(script: String)

    suspend fun initCoreAndWait(script: String)

    fun destroy()
}
