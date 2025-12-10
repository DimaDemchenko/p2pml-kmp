package com.novage.p2pml.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.novage.p2pml.eventEmitter.EventEmitter

class AndroidWebViewFactory(private val context: Context) : WebViewFactory {
    override fun createHeadlessWebView(
        eventEmitter: EventEmitter,
        onWebviewLoaded: () -> Unit
    ): HeadlessWebView {
        return AndroidHeadlessWebView(context)
    }
}

private class AndroidHeadlessWebView(context: Context) : HeadlessWebView {
    @SuppressLint("SetJavaScriptEnabled")
    private val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        webViewClient = WebViewClient()
    }

    override fun loadUrl(url: String) {
        runOnUiThread {
            webView.loadUrl(url)
        }
    }

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        runOnUiThread {
            webView.evaluateJavascript(script) { result ->
                callback?.invoke(result)
            }
        }
    }

    override fun destroy() {
        runOnUiThread {
            webView.destroy()
        }
    }

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }
}