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
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.events.P2PEvents

internal class AndroidWebViewFactory(private val context: Context) : WebViewFactory {
    override fun createHeadlessWebView(
        events: P2PEvents,
        onFatalError: (P2PMediaLoaderException) -> Unit
    ): HeadlessWebView = AndroidHeadlessWebView(context, events, onFatalError)
}

private class AndroidHeadlessWebView(
    context: Context,
    events: P2PEvents,
    onFatalError: (P2PMediaLoaderException) -> Unit
) : BaseHeadlessWebView(onFatalError) {
    init {
        require(Looper.myLooper() == Looper.getMainLooper()) {
            "AndroidHeadlessWebView must be instantiated on the Main thread"
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    private var webView: WebView? = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        webViewClient = object : WebViewClient() {
            override fun onReceivedError(v: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request == null || !request.isForMainFrame) return
                // A main-frame abort can only come from our own stopLoading() — not an engine fault.
                if (error?.description?.toString() == "net::ERR_ABORTED") return
                handleError("WebView Error: ${error?.errorCode} ${error?.description}")
            }

            override fun onReceivedHttpError(
                v: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request == null || !request.isForMainFrame) return
                handleError("WebView HTTP Error: ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase}")
            }

            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                handleError("WebView Renderer Crashed. Did crash: ${detail?.didCrash()}")

                webView?.destroy()
                webView = null
                return true
            }
        }

        val dispatcher = AndroidWebViewEventDispatcher(
            events = events,
            onPageReady = { notifyPageReady() },
            onCoreInitResult = { error -> handleCoreInitResult(error) }
        )
        addJavascriptInterface(dispatcher, "P2PMLAndroid")
    }

    override fun postToMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    override fun isWebViewAlive(): Boolean = webView != null

    override fun startLoad(url: String): Exception? {
        webView?.loadUrl(url)
        return null
    }

    override fun stopLoading() {
        webView?.stopLoading()
    }

    override fun evaluateFireAndForget(script: String) {
        webView?.evaluateJavascript(script, null)
    }

    override fun evaluateInitScript(script: String) {
        // evaluateJavascript's callback carries only the completion value, never JS errors
        // (those go to the chromium console), so unlike iOS there is nothing to log here.
        webView?.evaluateJavascript(script, null)
    }

    override fun teardownWebView() {
        webView?.stopLoading()
        webView?.destroy()
        webView = null
    }
}
