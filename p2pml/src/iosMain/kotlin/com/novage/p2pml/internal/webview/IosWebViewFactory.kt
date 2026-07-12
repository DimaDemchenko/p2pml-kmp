package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.errors.P2PMediaLoaderErrorCode
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.api.logging.P2PLogging
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
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
        events: P2PEvents,
        onFatalError: (P2PMediaLoaderException) -> Unit
    ): HeadlessWebView = IosHeadlessWebView(events, onFatalError)
}

private class IosHeadlessWebView(
    private val events: P2PEvents,
    private val onFatalError: (P2PMediaLoaderException) -> Unit
) : HeadlessWebView {
    private var webView: WKWebView? = null

    private var navigationDelegate: NavigationDelegate? = null

    private var loadUrlContinuation: CancellableContinuation<Unit>? = null
    private var onPageReadyCallback: (() -> Unit)? = null
    private var isDestroyed = false

    init {
        require(NSThread.isMainThread) { "IosHeadlessWebView must be instantiated on the main thread" }
        initWebView()
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
        if (P2PLogging.isDebugEnabled) {
            preferences.setValue(true, forKey = "developerExtrasEnabled")
        }
        configuration.preferences = preferences

        val scriptMessageHandler = IosWebViewEventDispatcher(events) {
            onPageReadyCallback?.invoke()
        }

        IosBridgeChannels.all.forEach { channel ->
            configuration.userContentController.addScriptMessageHandler(scriptMessageHandler, channel)
        }

        val frame = CGRectZero.readValue()
        val wkWebView = WKWebView(frame = frame, configuration = configuration)

        val delegate = NavigationDelegate { msg ->
            val cont = loadUrlContinuation
            if (cont != null) {
                if (cont.isActive) {
                    cont.resumeWithException(
                        P2PMediaLoaderException(P2PMediaLoaderErrorCode.ENGINE_LOAD_FAILED, msg)
                    )
                }
                loadUrlContinuation = null
                onPageReadyCallback = null
            } else if (!isDestroyed) {
                onFatalError(P2PMediaLoaderException(P2PMediaLoaderErrorCode.ENGINE_CRASHED, msg))
            }
            // If isDestroyed, the error is a late callback from our own teardown (e.g. stopLoading()
            // aborting navigation) — not a runtime fault, so it must not be surfaced as fatal.
        }

        this.navigationDelegate = delegate
        wkWebView.navigationDelegate = delegate

        wkWebView.hidden = true
        wkWebView.userInteractionEnabled = false
        wkWebView.inspectable = P2PLogging.isDebugEnabled

        this.webView = wkWebView
    }

    override suspend fun loadUrlAndWait(url: String) = suspendCancellableCoroutine<Unit> { continuation ->
        runOnMainThread {
            if (!continuation.isActive) return@runOnMainThread

            val view = webView
            if (view == null) {
                continuation.resumeWithException(IllegalStateException("WebView is destroyed"))
                return@runOnMainThread
            }

            if (this.loadUrlContinuation != null) {
                continuation.resumeWithException(IllegalStateException("A load is already in progress"))
                return@runOnMainThread
            }

            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl == null) {
                continuation.resumeWithException(IllegalArgumentException("Invalid URL: $url"))
                return@runOnMainThread
            }

            this.loadUrlContinuation = continuation
            this.onPageReadyCallback = {
                if (continuation.isActive) continuation.resume(Unit)
                this.loadUrlContinuation = null
                this.onPageReadyCallback = null
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
            isDestroyed = true
            loadUrlContinuation?.cancel(CancellationException("WebView destroyed"))
            loadUrlContinuation = null
            onPageReadyCallback = null

            val view = webView ?: return@runOnMainThread

            IosBridgeChannels.all.forEach { channel ->
                view.configuration.userContentController.removeScriptMessageHandlerForName(channel)
            }

            view.stopLoading()
            view.removeFromSuperview()
            view.navigationDelegate = null

            navigationDelegate = null
            webView = null
        }
    }
}

private class NavigationDelegate(private val onError: (String) -> Unit) :
    NSObject(),
    WKNavigationDelegateProtocol {

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        val msg = "WebView Error: ${withError.code} ${withError.localizedDescription}"
        onError(msg)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        val msg = "WebView Navigation Error: ${withError.code} ${withError.localizedDescription}"
        onError(msg)
    }

    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
        val msg = "WKWebView Web Content Process Terminated"
        onError(msg)
    }
}
