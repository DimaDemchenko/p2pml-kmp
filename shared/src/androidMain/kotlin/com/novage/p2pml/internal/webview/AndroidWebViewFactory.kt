package com.novage.p2pml.internal.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.novage.p2pml.MediaLoaderErrorType
import com.novage.p2pml.internal.events.CoreEventEmitter

internal class AndroidWebViewFactory(private val context: Context) : WebViewFactory {
    override fun createHeadlessWebView(
        eventEmitter: CoreEventEmitter,
        onWebViewLoaded: () -> Unit,
        onWebViewError: (MediaLoaderErrorType, String) -> Unit
    ): HeadlessWebView = AndroidHeadlessWebView(context, eventEmitter, onWebViewLoaded, onWebViewError)
}

private class AndroidHeadlessWebView(
    context: Context,
    eventEmitter: CoreEventEmitter,
    private val onWebViewLoaded: () -> Unit,
    private val onWebViewError: (MediaLoaderErrorType, String) -> Unit
) : HeadlessWebView {
    @SuppressLint("SetJavaScriptEnabled")
    private var webView: WebView? = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request == null || !request.isForMainFrame) return

                onWebViewError(
                    MediaLoaderErrorType.ENGINE_RUNTIME_ERROR,
                    "WebView Error: ${error?.errorCode} ${error?.description}"
                )
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request == null || !request.isForMainFrame) return

                onWebViewError(
                    MediaLoaderErrorType.ENGINE_RUNTIME_ERROR,
                    "WebView HTTP Error: ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase}"
                )
            }
        }

        val dispatcher = AndroidWebViewEventDispatcher(
            eventEmitter = eventEmitter,
            onPageReady = onWebViewLoaded
        )

        addJavascriptInterface(dispatcher, "P2PMLAndroid")
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun loadUrl(url: String) {
        runOnUiThread {
            webView?.loadUrl(url)
        }
    }

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        runOnUiThread {
            webView?.evaluateJavascript(script) { result ->
                callback?.invoke(result)
            }
        }
    }

    override fun destroy() {
        runOnUiThread {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }
}
