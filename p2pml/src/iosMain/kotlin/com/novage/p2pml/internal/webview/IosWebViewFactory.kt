package com.novage.p2pml.internal.webview

import com.novage.p2pml.P2PMediaLoaderErrorType
import com.novage.p2pml.P2PMediaLoaderException
import com.novage.p2pml.api.events.P2PEventRegistry
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.setValue
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKPreferences
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal class IosWebViewFactory : WebViewFactory {
    override fun createHeadlessWebView(
        events: P2PEventRegistry,
        onFatalError: (P2PMediaLoaderException) -> Unit
    ): HeadlessWebView = IosHeadlessWebView(events, onFatalError)
}

private class IosHeadlessWebView(
    private val events: P2PEventRegistry,
    private val onFatalError: (P2PMediaLoaderException) -> Unit
) : HeadlessWebView {
    private var webView: WKWebView? = null

    private var navigationDelegate: NavigationDelegate? = null

    private var loadUrlContinuation: CancellableContinuation<Unit>? = null
    private var onPageReadyCallback: (() -> Unit)? = null

    init {
        runOnMainThread { initWebView() }
    }

    private inline fun runOnMainThread(crossinline block: () -> Unit) {
        if (NSThread.isMainThread) {
            block()
        } else {
            dispatch_async(dispatch_get_main_queue()) { block() }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun initWebView() {
        val configuration = WKWebViewConfiguration()

        val preferences = WKPreferences()
        preferences.setValue(true, forKey = "developerExtrasEnabled")
        configuration.preferences = preferences

        val scriptMessageHandler = IosWebViewEventDispatcher(events) {
            onPageReadyCallback?.invoke()
        }
        configuration.userContentController.addScriptMessageHandler(scriptMessageHandler, "p2pml")

        val frame = CGRectZero.readValue()
        val wkWebView = WKWebView(frame = frame, configuration = configuration)

        val delegate = NavigationDelegate(onFatalError) { exception ->
            val cont = loadUrlContinuation
            if (cont != null && cont.isActive) {
                cont.resumeWithException(exception)
                loadUrlContinuation = null
                onPageReadyCallback = null
            } else {
                onFatalError(
                    P2PMediaLoaderException(
                        P2PMediaLoaderErrorType.ENGINE_RUNTIME_ERROR,
                        exception.message ?: "Unknown error"
                    )
                )
            }
        }
        
        this.navigationDelegate = delegate
        wkWebView.navigationDelegate = delegate

        wkWebView.hidden = true
        wkWebView.userInteractionEnabled = false
        wkWebView.inspectable = true

        this.webView = wkWebView
    }

    override suspend fun loadUrlAndWait(url: String) = suspendCancellableCoroutine<Unit> { continuation ->
        runOnMainThread {
            this.loadUrlContinuation = continuation
            this.onPageReadyCallback = {
                if (continuation.isActive) continuation.resume(Unit)
                this.loadUrlContinuation = null
                this.onPageReadyCallback = null
            }

            val view = webView
            if (view == null) {
                continuation.resumeWithException(IllegalStateException("WebView is destroyed"))
                return@runOnMainThread
            }

            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl == null) {
                continuation.resumeWithException(IllegalArgumentException("Invalid URL: $url"))
                return@runOnMainThread
            }

            continuation.invokeOnCancellation {
                runOnMainThread {
                    view.stopLoading()
                    loadUrlContinuation = null
                    onPageReadyCallback = null
                }
            }

            val request = NSURLRequest.requestWithURL(nsUrl)
            view.loadRequest(request)
        }
    }

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        runOnMainThread {
            val view = webView ?: return@runOnMainThread

            view.evaluateJavaScript(script) { result, error ->
                if (error == null && result is String) {
                    callback?.invoke(result)
                } else {
                    callback?.invoke(null)
                }
            }
        }
    }

    override fun destroy() {
        runOnMainThread {
            loadUrlContinuation?.cancel(CancellationException("WebView destroyed"))
            loadUrlContinuation = null
            onPageReadyCallback = null

            val view = webView ?: return@runOnMainThread

            view.configuration.userContentController.removeScriptMessageHandlerForName("p2pml")
            view.stopLoading()
            view.removeFromSuperview()
            view.navigationDelegate = null

            navigationDelegate = null
            webView = null
        }
    }
}

private class NavigationDelegate(
    private val onFatalError: (P2PMediaLoaderException) -> Unit,
    private val onError: (P2PMediaLoaderException) -> Unit
) : NSObject(),
    WKNavigationDelegateProtocol {

    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        val msg = "WebView Error: ${withError.code} ${withError.localizedDescription}"
        onError(P2PMediaLoaderException(P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR, msg))
    }

    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
        val msg = "WKWebView Web Content Process Terminated"
        onFatalError(P2PMediaLoaderException(P2PMediaLoaderErrorType.ENGINE_RUNTIME_ERROR, msg))
    }
}
