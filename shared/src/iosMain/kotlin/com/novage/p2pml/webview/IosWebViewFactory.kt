package com.novage.p2pml.webview

import com.novage.p2pml.domain.interfaces.CoreEventEmitter
import com.novage.p2pml.domain.interfaces.HeadlessWebView
import com.novage.p2pml.domain.interfaces.WebViewFactory
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
        eventEmitter: CoreEventEmitter,
        onWebViewLoaded: () -> Unit,
        onWebViewError: (String) -> Unit
    ): HeadlessWebView = IosHeadlessWebView(eventEmitter, onWebViewLoaded, onWebViewError)
}

private class IosHeadlessWebView(
    private val eventEmitter: CoreEventEmitter,
    private val onWebViewLoaded: () -> Unit,
    private val onWebViewError: (String) -> Unit
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

        val scriptMessageHandler = IosWebViewEventDispatcher(eventEmitter) { onWebViewLoaded() }
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

private class NavigationDelegate(private val onError: (String) -> Unit) :
    NSObject(),
    WKNavigationDelegateProtocol {

    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        onError("Network Error: ${withError.localizedDescription} (${withError.code})")
    }
}
