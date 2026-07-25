package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.errors.P2PMediaLoaderErrorCode
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Shared continuation state machine for the platform headless WebViews.
 *
 * Owns the load/init await lifecycle and the error-routing rules. Subclasses supply the platform
 * mechanics (main-thread dispatch, WebView creation, load/evaluate/teardown) through the abstract
 * hooks, and drive the state machine by calling [notifyPageReady], [handleCoreInitResult] and
 * [handleError] from their bridge and navigation callbacks.
 *
 * The await state is mutated only on the main thread: the [HeadlessWebView] entry points and the
 * [notifyPageReady]/[handleCoreInitResult]/[handleError] callbacks all funnel through
 * [postToMainThread], so the callbacks are safe to invoke from any thread.
 */
internal abstract class BaseHeadlessWebView(private val onFatalError: (P2PMediaLoaderException) -> Unit) :
    HeadlessWebView {
    private var loadUrlContinuation: CancellableContinuation<Unit>? = null
    private var coreInitContinuation: CancellableContinuation<Unit>? = null
    private var onPageReadyCallback: (() -> Unit)? = null
    private var isDestroyed = false

    /** Runs [block] on the main thread — synchronously if already on it, otherwise posted. */
    protected abstract fun postToMainThread(block: () -> Unit)

    protected abstract fun isWebViewAlive(): Boolean

    /**
     * Starts the actual page load once the await state is armed (main thread). Returns a non-null
     * exception to reject the load — e.g. a malformed URL — in which case the base rolls the await
     * back and fails it; returns null once the load has been kicked off.
     */
    protected abstract fun startLoad(url: String): Exception?

    protected abstract fun stopLoading()

    protected abstract fun evaluateFireAndForget(script: String)

    /**
     * Evaluates the core-init [script]. The engine acknowledges asynchronously via
     * [handleCoreInitResult]; an implementation that can detect a synchronous evaluation failure
     * should report it through [handleCoreInitResult] so the await fails fast.
     */
    protected abstract fun evaluateInitScript(script: String)

    /** Releases the underlying WebView and any platform resources; must tolerate repeated calls. */
    protected abstract fun teardownWebView()

    final override suspend fun loadUrlAndWait(url: String) = suspendCancellableCoroutine<Unit> { continuation ->
        postToMainThread {
            if (!continuation.isActive) return@postToMainThread

            if (!isWebViewAlive()) {
                continuation.resumeWithException(IllegalStateException("WebView is destroyed"))
                return@postToMainThread
            }

            if (loadUrlContinuation != null) {
                continuation.resumeWithException(IllegalStateException("A load is already in progress"))
                return@postToMainThread
            }

            loadUrlContinuation = continuation
            onPageReadyCallback = {
                if (continuation.isActive) continuation.resume(Unit)
                loadUrlContinuation = null
                onPageReadyCallback = null
            }

            continuation.invokeOnCancellation {
                postToMainThread {
                    stopLoading()
                    loadUrlContinuation = null
                    onPageReadyCallback = null
                }
            }

            val rejection = startLoad(url)
            if (rejection != null) {
                loadUrlContinuation = null
                onPageReadyCallback = null
                continuation.resumeWithException(rejection)
            }
        }
    }

    final override fun evaluateJavascript(script: String) {
        postToMainThread { evaluateFireAndForget(script) }
    }

    final override suspend fun initCoreAndWait(script: String) = suspendCancellableCoroutine<Unit> { continuation ->
        postToMainThread {
            if (!continuation.isActive) return@postToMainThread

            if (!isWebViewAlive()) {
                continuation.resumeWithException(IllegalStateException("WebView is destroyed"))
                return@postToMainThread
            }

            if (coreInitContinuation != null) {
                continuation.resumeWithException(IllegalStateException("A core init is already in progress"))
                return@postToMainThread
            }

            coreInitContinuation = continuation

            continuation.invokeOnCancellation {
                postToMainThread { coreInitContinuation = null }
            }

            evaluateInitScript(script)
        }
    }

    final override fun destroy() {
        postToMainThread {
            isDestroyed = true
            loadUrlContinuation?.cancel(CancellationException("WebView destroyed"))
            loadUrlContinuation = null
            coreInitContinuation?.cancel(CancellationException("WebView destroyed"))
            coreInitContinuation = null
            onPageReadyCallback = null

            teardownWebView()
        }
    }

    /** Signals that the page finished loading; resumes a pending [loadUrlAndWait]. Safe from any thread. */
    protected fun notifyPageReady() {
        postToMainThread { onPageReadyCallback?.invoke() }
    }

    /** Delivers the engine's core-init acknowledgement; resumes a pending [initCoreAndWait]. Any thread. */
    protected fun handleCoreInitResult(errorMessage: String?) {
        postToMainThread {
            val cont = takeCoreInitContinuation() ?: return@postToMainThread

            if (errorMessage == null) {
                cont.resume(Unit)
            } else {
                cont.resumeWithException(
                    P2PMediaLoaderException(P2PMediaLoaderErrorCode.ENGINE_INIT_FAILED, errorMessage)
                )
            }
        }
    }

    private fun takeCoreInitContinuation(): CancellableContinuation<Unit>? {
        val cont = coreInitContinuation
        coreInitContinuation = null
        return cont?.takeIf { it.isActive }
    }

    /** Routes a WebView-level error to whichever await is pending, else to [onFatalError]. Any thread. */
    protected fun handleError(msg: String) {
        postToMainThread {
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
}
