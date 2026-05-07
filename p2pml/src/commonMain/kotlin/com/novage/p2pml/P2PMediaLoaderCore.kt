package com.novage.p2pml

import com.novage.p2pml.api.events.P2PEventRegistry
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.internal.session.P2PSession
import com.novage.p2pml.internal.session.P2PSessionFactory
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.utils.LogConfig
import com.novage.p2pml.internal.utils.RuntimeErrorDispatcher
import com.novage.p2pml.internal.webview.HeadlessWebView
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

private enum class LoaderStatus { IDLE, INITIALIZING, ACTIVE, RELEASING, RELEASED }

internal class P2PMediaLoaderCore(
    private val coreConfig: CoreConfig = CoreConfig(),
    private val customEngineUrl: String? = null
) {
    companion object {
        fun enableLogging() {
            LogConfig.isEnabled = true
        }
        fun disableLogging() {
            LogConfig.isEnabled = false
        }
    }

    private val logger = CoreLogger("P2PMediaLoaderCore")
    private val errorDispatcher = RuntimeErrorDispatcher()
    private val sessionFactory = P2PSessionFactory(
        coreConfig = coreConfig,
        errorDispatcher = errorDispatcher,
        customEngineUrl = customEngineUrl
    )
    private val coreScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var activeSession: P2PSession? = null

    private val status = MutableStateFlow(LoaderStatus.IDLE)
    private var pendingDynamicConfig: DynamicCoreConfig? = null

    val fatalErrors = errorDispatcher.errors
    val events: P2PEventRegistry =
        P2PEventRegistry(coreScope, { activeSession?.engineManager }, { status.value == LoaderStatus.ACTIVE })

    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    internal suspend fun initialize(
        provider: PlaybackProvider,
        webViewFactory: (onFatalError: (P2PMediaLoaderException) -> Unit) -> HeadlessWebView
    ) {
        withContext(Dispatchers.Default) {
            if (!status.compareAndSet(LoaderStatus.IDLE, LoaderStatus.INITIALIZING)) {
                val message = "Initialization skipped: Core is already in state ${status.value}"
                logger.w { message }
                throw P2PMediaLoaderException(P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR, message)
            }

            logger.d { "Initializing P2PMediaLoaderCore..." }
            monitorRuntimeErrors()

            runCatching {
                performSessionInitialization(provider, webViewFactory)
            }.onFailure { e ->
                release()
                val mappedException = if (e !is Exception) e else handleInitializationException(e)
                throw mappedException
            }
        }
    }

    private suspend fun performSessionInitialization(
        provider: PlaybackProvider,
        webViewFactory: (onFatalError: (P2PMediaLoaderException) -> Unit) -> HeadlessWebView
    ) {
        val session = sessionFactory.createSession(
            provider = provider,
            webViewFactory = webViewFactory
        )

        this@P2PMediaLoaderCore.activeSession = session

        if (!status.compareAndSet(LoaderStatus.INITIALIZING, LoaderStatus.ACTIVE)) {
            this@P2PMediaLoaderCore.activeSession = null
            logger.w { "Initialization aborted: Core state changed to ${status.value} during session creation." }

            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { session.destroy() }.onFailure { e ->
                    logger.e(e) { "Error destroying orphaned session: ${e.message}" }
                }
            }

            throw CancellationException("Session initialization aborted due to concurrent release.")
        }

        events.syncEarlySubscriptions()

        pendingDynamicConfig?.let {
            logger.i { "Applying cached pending dynamic config..." }
            session.applyDynamicConfig(it)
            pendingDynamicConfig = null
        }
    }

    private fun handleInitializationException(e: Exception): Exception = when (e) {
        is TimeoutCancellationException -> {
            logger.e { "Initialization timed out waiting for WebView." }
            P2PMediaLoaderException(P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR, "WebView timeout", e)
        }

        is CancellationException -> {
            logger.d { "Initialization cancelled by coroutine scope." }
            e
        }

        is P2PMediaLoaderException -> {
            logger.e { "Initialization failed: ${e.message}" }
            e
        }

        else -> {
            logger.e { "Initialization failed due to system error: ${e.message}" }
            P2PMediaLoaderException(
                P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR,
                e.message ?: "System error",
                e
            )
        }
    }

    private fun monitorRuntimeErrors() {
        errorDispatcher.errors.onEach { exception ->
            logger.e { "Runtime System Error Caught: ${exception.type} - ${exception.message}" }
            if (exception.type == P2PMediaLoaderErrorType.ENGINE_RUNTIME_ERROR) {
                release()
            }
        }.launchIn(coreScope)
    }

    @Throws(P2PMediaLoaderException::class)
    fun createPlaybackUrl(manifestUrl: String): String {
        if (status.value != LoaderStatus.ACTIVE) {
            throw P2PMediaLoaderException(
                P2PMediaLoaderErrorType.CORE_NOT_INITIALIZED_ERROR,
                "P2PMediaLoader is not ready. Current state: ${status.value}"
            )
        }
        return activeSession?.createPlaybackUrl(manifestUrl.encodeURLParameter())
            ?: throw P2PMediaLoaderException(
                P2PMediaLoaderErrorType.CORE_NOT_INITIALIZED_ERROR,
                "Internal invariant violation: activeSession is null while status is ${status.value}"
            )
    }

    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) {
        when (status.value) {
            LoaderStatus.IDLE, LoaderStatus.INITIALIZING -> pendingDynamicConfig = dynamicCoreConfig

            LoaderStatus.ACTIVE -> {
                val session = activeSession ?: throw P2PMediaLoaderException(
                    P2PMediaLoaderErrorType.CORE_NOT_INITIALIZED_ERROR,
                    "Internal invariant violation: activeSession is null while status is ${status.value}"
                )
                session.applyDynamicConfig(dynamicCoreConfig)
            }

            LoaderStatus.RELEASING, LoaderStatus.RELEASED -> logger.w {
                "Ignored dynamic config. Core state: ${status.value}."
            }
        }
    }

    fun release() {
        status.update { current ->
            if (current != LoaderStatus.ACTIVE && current != LoaderStatus.INITIALIZING) {
                return
            }
            LoaderStatus.RELEASING
        }

        logger.i { "Releasing P2PMediaLoaderCore resources..." }

        val sessionToDestroy = activeSession
        activeSession = null
        pendingDynamicConfig = null

        coreScope.coroutineContext.cancelChildren()

        coreScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    sessionToDestroy?.destroy()
                } catch (e: CancellationException) {
                    logger.d { "Teardown cancelled." }
                    throw e
                } catch (e: IOException) {
                    logger.e { "IO Error during session teardown: ${e.message}" }
                } catch (e: IllegalStateException) {
                    logger.e { "State Error during session teardown: ${e.message}" }
                } catch (e: IllegalArgumentException) {
                    logger.e { "Arg Error during session teardown: ${e.message}" }
                } finally {
                    status.value = LoaderStatus.RELEASED
                    logger.d { "Release complete." }

                    coreScope.cancel()
                }
            }
        }
    }
}
