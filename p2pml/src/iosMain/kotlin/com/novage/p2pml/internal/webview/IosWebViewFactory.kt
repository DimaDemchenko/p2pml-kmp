package com.novage.p2pml.internal.webview

import com.novage.p2pml.P2PMediaLoaderErrorType
import com.novage.p2pml.api.events.P2PEventRegistry
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
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
        onWebViewLoaded: () -> Unit,
        onWebViewError: (P2PMediaLoaderErrorType, String) -> Unit
    ): HeadlessWebView = IosHeadlessWebView(events, onWebViewLoaded, onWebViewError)
}

private class IosHeadlessWebView(
    private val events: P2PEventRegistry,
    private val onWebViewLoaded: () -> Unit,
    private val onWebViewError: (P2PMediaLoaderErrorType, String) -> Unit
) : HeadlessWebView {
    private var webView: WKWebView? = null

    private var navigationDelegate: NavigationDelegate? = null

    init {
        dispatch_async(dispatch_get_main_queue()) {
            initWebView()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun initWebView() {
        val configuration = WKWebViewConfiguration()

        val scriptMessageHandler = IosWebViewEventDispatcher(events) { onWebViewLoaded() }
        configuration.userContentController.addScriptMessageHandler(scriptMessageHandler, "p2pml")

        val preferences = WKPreferences()
        preferences.setValue(true, forKey = "developerExtrasEnabled")
        configuration.preferences = preferences

        val frame = CGRectZero.readValue()
        val wkWebView = WKWebView(frame = frame, configuration = configuration)

        val delegate = NavigationDelegate(onWebViewError)
        this.navigationDelegate = delegate
        wkWebView.navigationDelegate = delegate

        wkWebView.hidden = true
        wkWebView.userInteractionEnabled = false
        wkWebView.inspectable = true

        this.webView = wkWebView
    }

    override fun loadUrl(url: String) {
        dispatch_async(dispatch_get_main_queue()) {
            val view = webView ?: return@dispatch_async

            val nsUrl = NSURL.URLWithString(url) ?: return@dispatch_async
            val request = NSURLRequest.requestWithURL(nsUrl)
            view.loadRequest(request)
        }
    }

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        dispatch_async(dispatch_get_main_queue()) {
            val view = webView ?: return@dispatch_async

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
        dispatch_async(dispatch_get_main_queue()) {
            val view = webView ?: return@dispatch_async

            view.configuration.userContentController.removeScriptMessageHandlerForName("p2pml")
            view.stopLoading()
            view.removeFromSuperview()
            view.navigationDelegate = null

            navigationDelegate = null
            webView = null
        }
    }
}

private class NavigationDelegate(private val onError: (P2PMediaLoaderErrorType, String) -> Unit) :
    NSObject(),
    WKNavigationDelegateProtocol {

    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        onError(
            P2PMediaLoaderErrorType.ENGINE_RUNTIME_ERROR,
            "WebView Error: ${withError.code} ${withError.localizedDescription}"
        )
    }
}
