package com.novage.p2pml.domain.interfaces

internal interface HeadlessWebView {
    fun loadUrl(url: String)

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?)

    fun destroy()
}
