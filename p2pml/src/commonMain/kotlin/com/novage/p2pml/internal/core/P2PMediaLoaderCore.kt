package com.novage.p2pml.internal.core

import com.novage.p2pml.api.errors.P2PMediaLoaderErrorType
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.internal.session.P2PSession
import com.novage.p2pml.internal.session.P2PSessionFactory
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.utils.LogConfig
import com.novage.p2pml.internal.utils.RuntimeErrorDispatcher
import com.novage.p2pml.internal.webview.WebViewFactory
import io.ktor.http.encodeURLParameter
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

private enum class LoaderStatus { IDLE, INITIALIZING, ACTIVE, RELEASING, RELEASED }

/**
 * Core orchestrator for P2P media streaming. Single-use: once [release] is called,
 * the internal [CoroutineScope] is cancelled permanently and this instance must be discarded.
 */
@OptIn(ExperimentalAtomicApi::class)
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
    private val cleanupScope by lazy {
        CoroutineScope(
            Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                logger.e { "Uncaught error during session teardown: ${e.message}" }
            }
        )
    }

    private val activeSession = AtomicReference<P2PSession?>(null)

    private val status = MutableStateFlow(LoaderStatus.IDLE)

    private val pendingDynamicConfig = MutableStateFlow<DynamicCoreConfig?>(null)

    val runtimeErrors = errorDispatcher.errors
    val p2pEvents: P2PEvents = P2PEvents(
        coreScope = coreScope,
        onSubscribe = { eventName -> activeSession.load()?.subscribeToEvent(eventName) },
        onUnsubscribe = { eventName -> activeSession.load()?.unsubscribeFromEvent(eventName) },
        isCoreActive = { status.value == LoaderStatus.ACTIVE }
    )

    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    internal suspend fun initialize(provider: PlaybackProvider, webViewFactory: WebViewFactory) {
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

    private suspend fun performSessionInitialization(provider: PlaybackProvider, webViewFactory: WebViewFactory) {
        val session = sessionFactory.createSession(
            provider = provider,
            webViewFactory = webViewFactory,
            events = p2pEvents
        )

        activeSession.store(session)

        val configToApply = pendingDynamicConfig.getAndUpdate { null }
        if (configToApply != null) {
            logger.i { "Applying cached pending dynamic config..." }
            session.applyDynamicConfig(configToApply)
        }

        if (!status.compareAndSet(LoaderStatus.INITIALIZING, LoaderStatus.ACTIVE)) {
            val orphanedSession = if (activeSession.compareAndSet(session, null)) session else null

            logger.w { "Initialization aborted: Core state changed to ${status.value} during session creation." }

            if (orphanedSession != null) {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { orphanedSession.destroy() }.onFailure { e ->
                        logger.e(e) { "Error destroying orphaned session: ${e.message}" }
                    }
                }
            }

            throw CancellationException("Session initialization aborted due to concurrent release.")
        }

        pendingDynamicConfig.getAndUpdate { null }?.let { late ->
            logger.i { "Applying dynamic config that arrived during initialization handoff." }
            session.applyDynamicConfig(late)
        }

        p2pEvents.syncEarlySubscriptions()
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
        val session = activeSession.load() ?: throw P2PMediaLoaderException(
            P2PMediaLoaderErrorType.CORE_NOT_INITIALIZED_ERROR,
            "P2PMediaLoader is not ready. Current state: ${status.value}"
        )
        return session.createPlaybackUrl(manifestUrl.encodeURLParameter())
    }

    @Throws(P2PMediaLoaderException::class)
    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) {
        val currentStatus = status.value
        if (currentStatus == LoaderStatus.RELEASING || currentStatus == LoaderStatus.RELEASED) {
            logger.w { "Ignored dynamic config. Core state: $currentStatus." }
            return
        }

        if (currentStatus == LoaderStatus.IDLE || currentStatus == LoaderStatus.INITIALIZING) {
            pendingDynamicConfig.value = dynamicCoreConfig
            return
        }

        val session = activeSession.load()
        if (session == null) {
            logger.w { "Session was null during ACTIVE state; release likely in progress. Ignoring." }
            return
        }
        session.applyDynamicConfig(dynamicCoreConfig)
    }

    fun release() {
        val previousStatus = status.getAndUpdate { current ->
            if (current != LoaderStatus.ACTIVE && current != LoaderStatus.INITIALIZING) {
                current
            } else {
                LoaderStatus.RELEASING
            }
        }
        if (previousStatus != LoaderStatus.ACTIVE && previousStatus != LoaderStatus.INITIALIZING) {
            return
        }

        logger.i { "Releasing P2PMediaLoaderCore resources..." }

        val sessionToDestroy = activeSession.exchange(null)
        pendingDynamicConfig.value = null

        coreScope.cancel()

        cleanupScope.launch {
            try {
                sessionToDestroy?.destroy()
            } catch (e: IOException) {
                logger.e { "IO Error during session teardown: ${e.message}" }
            } catch (e: IllegalStateException) {
                logger.e { "State Error during session teardown: ${e.message}" }
            } catch (e: IllegalArgumentException) {
                logger.e { "Arg Error during session teardown: ${e.message}" }
            } finally {
                status.value = LoaderStatus.RELEASED
                logger.d { "Release complete." }
                cleanupScope.cancel()
            }
        }
    }
}
