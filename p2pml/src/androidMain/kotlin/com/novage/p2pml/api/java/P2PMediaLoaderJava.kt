package com.novage.p2pml.api.java

import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.P2PMediaLoader
import com.novage.p2pml.api.config.DynamicCoreConfig
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.playback.PlaybackProvider
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.future.future

/**
 * Java-friendly facade over [P2PMediaLoader]: `suspend` functions are exposed as
 * [CompletableFuture]s and event flows as listener subscriptions. Wrap a configured
 * [P2PMediaLoader] and drive it entirely through this class.
 *
 * Single-use, mirroring [P2PMediaLoader]: once [release] is called the instance cannot be reused —
 * create a new one to restart.
 *
 * All listener callbacks run on a background thread (`Dispatchers.Default`); switch to the main
 * thread before touching UI.
 */
class P2PMediaLoaderJava(private val loader: P2PMediaLoader) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Subscribes to **all** P2P engine events.
     *
     * This turns on every event in the JS engine — including the high-frequency
     * [P2PEventType.CHUNK_DOWNLOADED] and [P2PEventType.CHUNK_UPLOADED] chunk statistics, which
     * cost a WebView bridge call per transferred chunk — even for callbacks the listener does not
     * override. If you only consume some events, prefer the overload taking [P2PEventType]s.
     *
     * **Important:** All listener callbacks are invoked on a background thread (`Dispatchers.Default`).
     * If you need to update the UI or interact with views inside a callback, you must explicitly
     * switch to the main thread.
     *
     * **Java Example:**
     * ```java
     * AutoCloseable subscription = loader.addListener(new P2PEventListener() {
     *     @Override
     *     public void onPeerConnect(PeerDetails details) {
     *         new Handler(Looper.getMainLooper()).post(() -> {
     *             // Update UI here
     *         });
     *     }
     * });
     *
     * // Later, when you want to stop listening and avoid leaks:
     * try {
     *     subscription.close();
     * } catch (Exception e) {
     *     e.printStackTrace();
     * }
     * ```
     *
     * @param listener The listener to receive event callbacks.
     * @return An [AutoCloseable] that cancels the subscriptions when closed.
     */
    fun addListener(listener: P2PEventListener): AutoCloseable = subscribe(listener, P2PEventType.entries.toSet())

    /**
     * Subscribes to the given P2P engine [events] only.
     *
     * The JS engine is subscribed to exactly these events; the listener's other callbacks are
     * never invoked and cause no bridge traffic.
     *
     * Callbacks are invoked on a background thread (`Dispatchers.Default`); switch to the main
     * thread before touching UI.
     *
     * **Java Example:**
     * ```java
     * AutoCloseable subscription = loader.addListener(listener,
     *     P2PEventType.PEER_CONNECT, P2PEventType.PEER_CLOSE);
     * ```
     *
     * @param listener The listener to receive event callbacks.
     * @param events The events to subscribe to; duplicates are ignored.
     * @return An [AutoCloseable] that cancels the subscriptions when closed.
     * @throws IllegalArgumentException if [events] is empty.
     */
    fun addListener(listener: P2PEventListener, vararg events: P2PEventType): AutoCloseable {
        require(events.isNotEmpty()) {
            "events must not be empty; use addListener(listener) to subscribe to all events"
        }
        return subscribe(listener, events.toSet())
    }

    private fun subscribe(listener: P2PEventListener, events: Set<P2PEventType>): AutoCloseable {
        val jobs = events.map { it.collect(loader.p2pEvents, listener, scope) }
        return AutoCloseable { jobs.forEach { it.cancel() } }
    }

    /**
     * Observes loader [com.novage.p2pml.api.state.P2PMediaLoaderState] changes (lifecycle + fatal errors).
     *
     * The current state is delivered immediately on subscription (latched), so a listener added after
     * a terminal `FAILED` still observes it. On `FAILED`, fall back to the origin URL.
     *
     * Callbacks run on a background thread (`Dispatchers.Default`); hop to the main thread before
     * touching UI. Note: [release] cancels this subscription, so a `RELEASED` emitted by your own
     * [release] call is not guaranteed to be delivered.
     *
     * @return An [AutoCloseable] that stops delivery when closed.
     */
    fun addStateListener(listener: P2PMediaLoaderStateListener): AutoCloseable {
        val job = loader.state.onEach(listener::onStateChanged).launchIn(scope)
        return AutoCloseable { job.cancel() }
    }

    /**
     * Initializes the loader with a custom [PlaybackProvider]. The returned future completes when
     * startup succeeds, or completes exceptionally with [P2PMediaLoaderException] on failure.
     *
     * The caller owns the provider's lifecycle and must release it when done — the loader does not
     * release custom providers.
     */
    fun initialize(provider: PlaybackProvider): CompletableFuture<Void?> = scope.future {
        loader.initialize(provider)
        null
    }

    /**
     * Initializes the loader with an [ExoPlayer]. The returned future completes when startup
     * succeeds, or completes exceptionally with [P2PMediaLoaderException] on failure.
     *
     * The loader creates and manages an internal playback provider for the player and releases it
     * when [release] is called.
     */
    fun initialize(exoPlayer: ExoPlayer): CompletableFuture<Void?> = scope.future {
        loader.initialize(exoPlayer)
        null
    }

    /** See [P2PMediaLoader.createPlaybackUrl]: one active stream per loader. */
    @Throws(P2PMediaLoaderException::class)
    fun createPlaybackUrl(manifestUrl: String): String = loader.createPlaybackUrl(manifestUrl)

    /** See [P2PMediaLoader.applyDynamicConfig]: partial patch; pre-initialization calls are last-wins. */
    fun applyDynamicConfig(config: DynamicCoreConfig) = loader.applyDynamicConfig(config)

    /**
     * Cancels all listener/state subscriptions and releases the underlying [P2PMediaLoader].
     * Idempotent; the instance cannot be reused afterwards.
     */
    fun release() {
        scope.cancel()
        loader.release()
    }
}
