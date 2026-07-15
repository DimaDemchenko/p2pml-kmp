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
import com.novage.p2pml.api.errors.P2PMediaLoaderErrorCode
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.events.P2PEvents
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class AndroidWebViewFactory(private val context: Context) : WebViewFactory {
    override fun createHeadlessWebView(
        events: P2PEvents,
        onFatalError: (P2PMediaLoaderException) -> Unit
    ): HeadlessWebView = AndroidHeadlessWebView(context, events, onFatalError)
}

private class AndroidHeadlessWebView(
    context: Context,
    private val events: P2PEvents,
    private val onFatalError: (P2PMediaLoaderException) -> Unit
) : HeadlessWebView {
    private var loadUrlContinuation: CancellableContinuation<Unit>? = null
    private var coreInitContinuation: CancellableContinuation<Unit>? = null
    private var onPageReadyCallback: (() -> Unit)? = null
    private var isDestroyed = false

    init {
        require(Looper.myLooper() == Looper.getMainLooper()) {
            "AndroidHeadlessWebView must be instantiated on the Main thread"
        }
    }

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

                webView?.destroy()
                webView = null
                return true
            }
        }

        val dispatcher = AndroidWebViewEventDispatcher(
            events = events,
            onPageReady = { onPageReadyCallback?.invoke() },
            onCoreInitResult = { error -> handleCoreInitResult(error) }
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

    override fun evaluateJavascript(script: String) {
        runOnUiThread {
            webView?.evaluateJavascript(script, null)
        }
    }

    override suspend fun initCoreAndWait(script: String) = suspendCancellableCoroutine<Unit> { continuation ->
        runOnUiThread {
            if (!continuation.isActive) return@runOnUiThread

            val view = webView
            if (view == null) {
                continuation.resumeWithException(IllegalStateException("WebView is destroyed"))
                return@runOnUiThread
            }

            if (this.coreInitContinuation != null) {
                continuation.resumeWithException(IllegalStateException("A core init is already in progress"))
                return@runOnUiThread
            }

            this.coreInitContinuation = continuation

            continuation.invokeOnCancellation {
                runOnUiThread { coreInitContinuation = null }
            }

            // evaluateJavascript's callback carries only the completion value, never JS errors
            // (those go to the chromium console), so unlike iOS there is nothing to log here.
            view.evaluateJavascript(script, null)
        }
    }

    private fun handleCoreInitResult(errorMessage: String?) {
        val cont = takeCoreInitContinuation() ?: return

        if (errorMessage == null) {
            cont.resume(Unit)
        } else {
            cont.resumeWithException(
                P2PMediaLoaderException(P2PMediaLoaderErrorCode.ENGINE_INIT_FAILED, errorMessage)
            )
        }
    }

    private fun takeCoreInitContinuation(): CancellableContinuation<Unit>? {
        val cont = coreInitContinuation
        coreInitContinuation = null
        return cont?.takeIf { it.isActive }
    }

    override fun destroy() {
        runOnUiThread {
            isDestroyed = true
            loadUrlContinuation?.cancel(CancellationException("WebView destroyed"))
            loadUrlContinuation = null
            coreInitContinuation?.cancel(CancellationException("WebView destroyed"))
            coreInitContinuation = null
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
        val loadCont = loadUrlContinuation
        when {
            loadCont != null -> {
                loadUrlContinuation = null
                onPageReadyCallback = null
                if (loadCont.isActive) {
                    loadCont.resumeWithException(
                        P2PMediaLoaderException(P2PMediaLoaderErrorCode.ENGINE_LOAD_FAILED, msg)
                    )
                }
                // An inactive load continuation was cancelled by the startup timeout, the caller,
                // or destroy() — that party already owns the terminal report. Escalating this late
                // callback via onFatalError would race it with a conflicting error code
                // (ENGINE_CRASHED vs ENGINE_LOAD_TIMEOUT).
            }

            // Fail a pending init await directly; onFatalError would report the crash once and
            // the still-pending ack would time out into a second, conflicting failure. With no
            // active waiter (nothing pending, or the await already cancelled by the ack timeout)
            // the crash goes to onFatalError.
            else -> {
                val error = P2PMediaLoaderException(P2PMediaLoaderErrorCode.ENGINE_CRASHED, msg)
                val cont = takeCoreInitContinuation()
                if (cont != null) {
                    cont.resumeWithException(error)
                } else if (!isDestroyed) {
                    onFatalError(error)
                }
            }
        }
        // If isDestroyed, the error is a late callback from our own teardown (e.g. stopLoading()
        // aborting the in-flight load) — not a runtime fault, so it must not be surfaced as fatal.
    }
}
