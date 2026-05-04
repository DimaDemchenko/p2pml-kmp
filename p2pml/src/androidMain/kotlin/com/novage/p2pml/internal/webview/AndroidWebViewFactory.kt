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
import com.novage.p2pml.P2PMediaLoaderErrorType
import com.novage.p2pml.P2PMediaLoaderException
import com.novage.p2pml.api.events.P2PEventRegistry
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class AndroidWebViewFactory(private val context: Context) : WebViewFactory {
    override fun createHeadlessWebView(
        events: P2PEventRegistry,
        onFatalError: (P2PMediaLoaderException) -> Unit
    ): HeadlessWebView = AndroidHeadlessWebView(context, events, onFatalError)
}

private class AndroidHeadlessWebView(
    context: Context,
    private val events: P2PEventRegistry,
    private val onFatalError: (P2PMediaLoaderException) -> Unit
) : HeadlessWebView {
    private var loadUrlContinuation: CancellableContinuation<Unit>? = null
    private var onPageReadyCallback: (() -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    private var webView: WebView? = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        webViewClient = object : WebViewClient() {
            override fun onReceivedError(v: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request == null || !request.isForMainFrame) return
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
                return true
            }
        }

        val dispatcher = AndroidWebViewEventDispatcher(
            events = events,
            onPageReady = { onPageReadyCallback?.invoke() }
        )
        addJavascriptInterface(dispatcher, "P2PMLAndroid")
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun loadUrlAndWait(url: String) = suspendCancellableCoroutine<Unit> { continuation ->
        runOnUiThread {
            if (!continuation.isActive) return@runOnUiThread

            val view = webView
            if (view == null) {
                continuation.resumeWithException(IllegalStateException("WebView is destroyed"))
                return@runOnUiThread
            }

            if (this.loadUrlContinuation != null) {
                continuation.resumeWithException(IllegalStateException("A load is already in progress"))
                return@runOnUiThread
            }

            this.loadUrlContinuation = continuation
            this.onPageReadyCallback = {
                if (continuation.isActive) continuation.resume(Unit)
                this.loadUrlContinuation = null
                this.onPageReadyCallback = null
            }

            continuation.invokeOnCancellation {
                runOnUiThread {
                    view.stopLoading()
                    loadUrlContinuation = null
                    onPageReadyCallback = null
                }
            }

            view.loadUrl(url)
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
            loadUrlContinuation?.cancel(CancellationException("WebView destroyed"))
            loadUrlContinuation = null
            onPageReadyCallback = null

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

    private fun handleError(msg: String) {
        val cont = loadUrlContinuation
        if (cont != null && cont.isActive) {
            cont.resumeWithException(P2PMediaLoaderException(P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR, msg))
            loadUrlContinuation = null
            onPageReadyCallback = null
        } else {
            onFatalError(P2PMediaLoaderException(P2PMediaLoaderErrorType.ENGINE_RUNTIME_ERROR, msg))
        }
    }
}
