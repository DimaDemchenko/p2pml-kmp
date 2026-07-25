package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.api.logging.P2PLogging
import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLRequest
import platform.Foundation.setValue
import platform.WebKit.WKErrorDomain
import platform.WebKit.WKErrorJavaScriptResultTypeIsUnsupported
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

private class IosHeadlessWebView(events: P2PEvents, onFatalError: (P2PMediaLoaderException) -> Unit) :
    BaseHeadlessWebView(onFatalError) {
    private val logger = CoreLogger("IosHeadlessWebView")

    private var webView: WKWebView? = null
    private var navigationDelegate: NavigationDelegate? = null

    init {
        require(NSThread.isMainThread) { "IosHeadlessWebView must be instantiated on the main thread" }
        initWebView(events)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun initWebView(events: P2PEvents) {
        val configuration = WKWebViewConfiguration()

        val preferences = WKPreferences()
        if (P2PLogging.isDebugEnabled) {
            preferences.setValue(true, forKey = "developerExtrasEnabled")
        }
        configuration.preferences = preferences

        val scriptMessageHandler = IosWebViewEventDispatcher(
            events = events,
            onPageReady = { notifyPageReady() },
            onCoreInitResult = { error -> handleCoreInitResult(error) }
        )

        IosBridgeChannels.all.forEach { channel ->
            configuration.userContentController.addScriptMessageHandler(scriptMessageHandler, channel)
        }

        val frame = CGRectZero.readValue()
        val wkWebView = WKWebView(frame = frame, configuration = configuration)

        val delegate = NavigationDelegate(::handleError)

        this.navigationDelegate = delegate
        wkWebView.navigationDelegate = delegate

        wkWebView.hidden = true
        wkWebView.userInteractionEnabled = false

        if (wkWebView.respondsToSelector(NSSelectorFromString("setInspectable:"))) {
            wkWebView.inspectable = P2PLogging.isDebugEnabled
        }

        this.webView = wkWebView
    }

    override fun postToMainThread(block: () -> Unit) {
        if (NSThread.isMainThread) {
            block()
        } else {
            dispatch_async(dispatch_get_main_queue()) { block() }
        }
    }

    override fun isWebViewAlive(): Boolean = webView != null

    override fun startLoad(url: String): Exception? {
        val nsUrl = NSURL.URLWithString(url) ?: return IllegalArgumentException("Invalid URL: $url")
        webView?.loadRequest(NSURLRequest.requestWithURL(nsUrl))
        return null
    }

    override fun stopLoading() {
        webView?.stopLoading()
    }

    override fun evaluateFireAndForget(script: String) {
        webView?.evaluateJavaScript(script, completionHandler = null)
    }

    override fun evaluateInitScript(script: String) {
        // A script that throws outside initP2P's try/catch (e.g. missing window.p2p on a
        // custom page) sends no ack, so a genuine eval NSError means the init can never
        // complete — fail fast instead of burning the ack timeout with the cause buried in
        // the log. WKErrorJavaScriptResultTypeIsUnsupported only means the script's
        // completion value wasn't serializable (e.g. an async initP2P returning a Promise)
        // — not a failure.
        webView?.evaluateJavaScript(script) { _, error ->
            if (error != null &&
                !(error.domain == WKErrorDomain && error.code == WKErrorJavaScriptResultTypeIsUnsupported)
            ) {
                logger.e { "Core init script failed to evaluate: ${error.localizedDescription} ${error.userInfo}" }
                handleCoreInitResult("Core init script failed to evaluate: ${error.localizedDescription}")
            }
        }
    }

    override fun teardownWebView() {
        val view = webView ?: return

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

private class NavigationDelegate(private val onError: (String) -> Unit) :
    NSObject(),
    WKNavigationDelegateProtocol {

    private val logger = CoreLogger("WKNavigationDelegate")

    private fun isDeliberateCancellation(error: NSError): Boolean =
        error.domain == NSURLErrorDomain && error.code == NSURLErrorCancelled

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        if (isDeliberateCancellation(withError)) {
            logger.d { "Ignoring NSURLErrorCancelled from deliberate stopLoading()" }
            return
        }
        onError("WebView Error: ${withError.code} ${withError.localizedDescription}")
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        if (isDeliberateCancellation(withError)) {
            logger.d { "Ignoring NSURLErrorCancelled from deliberate stopLoading()" }
            return
        }
        onError("WebView Navigation Error: ${withError.code} ${withError.localizedDescription}")
    }

    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
        val msg = "WKWebView Web Content Process Terminated"
        onError(msg)
    }
}
