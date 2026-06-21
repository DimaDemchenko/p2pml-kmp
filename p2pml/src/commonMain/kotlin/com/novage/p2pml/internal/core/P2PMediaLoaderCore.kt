package com.novage.p2pml.internal.core

import com.novage.p2pml.api.config.CoreConfig
import com.novage.p2pml.api.config.DynamicCoreConfig
import com.novage.p2pml.api.errors.P2PMediaLoaderErrorCode
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.api.playback.PlaybackProvider
import com.novage.p2pml.api.state.P2PMediaLoaderState
import com.novage.p2pml.api.state.P2PMediaLoaderStatus
import com.novage.p2pml.internal.session.P2PSession
import com.novage.p2pml.internal.session.P2PSessionFactory
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.utils.LogConfig
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

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
    private val sessionFactory = P2PSessionFactory(
        coreConfig = coreConfig,
        onFatalError = { release(failure = it) },
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

    private val pendingDynamicConfig = MutableStateFlow<DynamicCoreConfig?>(null)

    private val _state = MutableStateFlow(P2PMediaLoaderState(P2PMediaLoaderStatus.IDLE))

    /** Observable, latched loader state. On [P2PMediaLoaderStatus.FAILED], fall back to the origin URL. */
    val state: StateFlow<P2PMediaLoaderState> = _state.asStateFlow()
    val p2pEvents: P2PEvents = P2PEvents(
        coreScope = coreScope,
        onSubscribe = { eventName -> activeSession.load()?.subscribeToEvent(eventName) },
        onUnsubscribe = { eventName -> activeSession.load()?.unsubscribeFromEvent(eventName) },
        isCoreActive = { _state.value.status == P2PMediaLoaderStatus.ACTIVE }
    )

    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    internal suspend fun initialize(provider: PlaybackProvider, webViewFactory: WebViewFactory) {
        withContext(Dispatchers.Default) {
            if (!_state.compareAndSet(
                    P2PMediaLoaderState(P2PMediaLoaderStatus.IDLE),
                    P2PMediaLoaderState(P2PMediaLoaderStatus.STARTING)
                )
            ) {
                val message = "Initialization skipped: Core is already in state ${_state.value.status}"
                logger.w { message }
                throw P2PMediaLoaderException(P2PMediaLoaderErrorCode.ENGINE_INIT_FAILED, message)
            }

            logger.d { "Initializing P2PMediaLoaderCore..." }

            runCatching {
                performSessionInitialization(provider, webViewFactory)
            }.onFailure { e ->
                val mappedException = if (e !is Exception) e else handleInitializationException(e)
                when (mappedException) {
                    is P2PMediaLoaderException -> release(failure = mappedException)
                    else -> release()
                }
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

        if (!_state.compareAndSet(
                P2PMediaLoaderState(P2PMediaLoaderStatus.STARTING),
                P2PMediaLoaderState(P2PMediaLoaderStatus.ACTIVE)
            )
        ) {
            val orphanedSession = if (activeSession.compareAndSet(session, null)) session else null

            logger.w {
                "Initialization aborted: Core state changed to ${_state.value.status} during session creation."
            }

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
            P2PMediaLoaderException(
                P2PMediaLoaderErrorCode.ENGINE_LOAD_TIMEOUT,
                "Engine page did not load within the startup timeout.",
                cause = e
            )
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
                P2PMediaLoaderErrorCode.ENGINE_INIT_FAILED,
                e.message ?: "System error",
                cause = e
            )
        }
    }

    @Throws(P2PMediaLoaderException::class)
    fun createPlaybackUrl(manifestUrl: String): String {
        val session = activeSession.load() ?: throw P2PMediaLoaderException(
            P2PMediaLoaderErrorCode.NOT_INITIALIZED,
            "P2PMediaLoader is not ready. Current state: ${_state.value.status}"
        )
        return session.createPlaybackUrl(manifestUrl.encodeURLParameter())
    }

    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) {
        val currentStatus = _state.value.status
        if (currentStatus == P2PMediaLoaderStatus.FAILED || currentStatus == P2PMediaLoaderStatus.RELEASED) {
            logger.w { "Ignored dynamic config. Core state: $currentStatus." }
            return
        }

        if (currentStatus == P2PMediaLoaderStatus.IDLE || currentStatus == P2PMediaLoaderStatus.STARTING) {
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

    /**
     * Terminally tears down the loader. Atomically claims the transition from a live state
     * ([P2PMediaLoaderStatus.STARTING]/[P2PMediaLoaderStatus.ACTIVE]) to a terminal one — [P2PMediaLoaderStatus.FAILED]
     * when [failure] is non-null, otherwise [P2PMediaLoaderStatus.RELEASED] — so teardown runs exactly once
     * and the fatal cause is latched for observers. A no-op if already terminal or never started.
     */
    fun release(failure: P2PMediaLoaderException? = null) {
        val previous = _state.getAndUpdate { current ->
            when (current.status) {
                P2PMediaLoaderStatus.STARTING, P2PMediaLoaderStatus.ACTIVE ->
                    if (failure != null) {
                        P2PMediaLoaderState(P2PMediaLoaderStatus.FAILED, failure)
                    } else {
                        P2PMediaLoaderState(P2PMediaLoaderStatus.RELEASED)
                    }

                else -> current
            }
        }
        if (previous.status != P2PMediaLoaderStatus.STARTING && previous.status != P2PMediaLoaderStatus.ACTIVE) {
            return
        }

        if (failure != null) {
            logger.e { "Fatal error — releasing P2PMediaLoaderCore: ${failure.code} - ${failure.message}" }
        }
        logger.i { "Releasing P2PMediaLoaderCore resources..." }

        val sessionToDestroy = activeSession.exchange(null)
        pendingDynamicConfig.value = null

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
                logger.d { "Release complete." }
                cleanupScope.cancel()
            }
        }

        coreScope.cancel()
    }
}
