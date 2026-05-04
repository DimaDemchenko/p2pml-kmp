package com.novage.p2pml.internal.webview

internal interface HeadlessWebView {
    suspend fun loadUrlAndWait(url: String)

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?)

    fun destroy()
}
