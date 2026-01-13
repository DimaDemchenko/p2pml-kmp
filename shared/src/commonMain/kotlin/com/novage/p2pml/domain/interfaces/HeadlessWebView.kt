package com.novage.p2pml.domain.interfaces

interface HeadlessWebView {
    fun loadUrl(url: String)

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?)

    fun destroy()
}
