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
                logger.e(e) { "Uncaught error during session teardown" }
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
                val message = "initialize() requires IDLE state; current state: ${_state.value.status}"
                logger.w { message }
                throw P2PMediaLoaderException(P2PMediaLoaderErrorCode.INVALID_STATE, message)
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

        drainPendingConfig(session, "cached before initialization")

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
                        logger.e(e) { "Error destroying orphaned session" }
                    }
                }
            }

            val latched = _state.value
            if (latched.status == P2PMediaLoaderStatus.FAILED && latched.error != null) {
                throw latched.error
            }
            throw CancellationException("Session initialization aborted due to concurrent release.")
        }

        drainPendingConfig(session, "arrived during initialization handoff")

        p2pEvents.syncEarlySubscriptions()
    }

    private fun handleInitializationException(e: Exception): Exception = when (e) {
        // Startup timeouts arrive pre-mapped by P2PSessionFactory.withStartupTimeout; a raw
        // TimeoutCancellationException here is an enclosing caller's timeout, i.e. cancellation.
        is CancellationException -> {
            logger.d { "Initialization cancelled by coroutine scope." }
            e
        }

        is P2PMediaLoaderException -> {
            logger.e(e) { "Initialization failed" }
            e
        }

        else -> {
            logger.e(e) { "Initialization failed due to system error" }
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
            if (_state.value.status == P2PMediaLoaderStatus.ACTIVE) {
                drainPendingConfig(activeSession.load(), "stored during activation handoff")
            }
            return
        }

        val session = activeSession.load()
        if (session == null) {
            logger.w { "Session was null during ACTIVE state; release likely in progress. Ignoring." }
            return
        }
        session.applyDynamicConfig(dynamicCoreConfig)
    }

    private fun drainPendingConfig(session: P2PSession?, context: String) {
        val pending = pendingDynamicConfig.getAndUpdate { null } ?: return
        if (session == null) return
        logger.i { "Applying pending dynamic config ($context)." }
        session.applyDynamicConfig(pending)
    }

    /**
     * Terminally tears down the loader. Atomically claims the transition from any non-terminal state
     * ([P2PMediaLoaderStatus.IDLE]/[P2PMediaLoaderStatus.STARTING]/[P2PMediaLoaderStatus.ACTIVE]) to a terminal
     * one — [P2PMediaLoaderStatus.FAILED] when [failure] is non-null, otherwise [P2PMediaLoaderStatus.RELEASED] —
     * so teardown runs exactly once and the fatal cause is latched for observers. Releasing an [P2PMediaLoaderStatus.IDLE]
     * loader latches the terminal state so a later [initialize] fails fast instead of booting a session
     * nobody will release. A no-op if already terminal.
     */
    fun release(failure: P2PMediaLoaderException? = null) {
        val previous = _state.getAndUpdate { current ->
            when (current.status) {
                P2PMediaLoaderStatus.IDLE, P2PMediaLoaderStatus.STARTING, P2PMediaLoaderStatus.ACTIVE ->
                    if (failure != null) {
                        P2PMediaLoaderState(P2PMediaLoaderStatus.FAILED, failure)
                    } else {
                        P2PMediaLoaderState(P2PMediaLoaderStatus.RELEASED)
                    }

                else -> current
            }
        }
        if (previous.status == P2PMediaLoaderStatus.IDLE) {
            logger.i { "Released before initialization — no session resources to clean up." }
            pendingDynamicConfig.value = null
            coreScope.cancel()
            return
        }
        if (previous.status != P2PMediaLoaderStatus.STARTING && previous.status != P2PMediaLoaderStatus.ACTIVE) {
            return
        }

        if (failure != null) {
            logger.e(failure) { "Fatal error — releasing P2PMediaLoaderCore: ${failure.code}" }
        }
        logger.i { "Releasing P2PMediaLoaderCore resources..." }

        val sessionToDestroy = activeSession.exchange(null)
        pendingDynamicConfig.value = null

        cleanupScope.launch {
            try {
                sessionToDestroy?.destroy()
            } catch (e: IOException) {
                logger.e(e) { "IO Error during session teardown" }
            } catch (e: IllegalStateException) {
                logger.e(e) { "State Error during session teardown" }
            } catch (e: IllegalArgumentException) {
                logger.e(e) { "Arg Error during session teardown" }
            } finally {
                logger.d { "Release complete." }
                cleanupScope.cancel()
            }
        }

        coreScope.cancel()
    }
}
