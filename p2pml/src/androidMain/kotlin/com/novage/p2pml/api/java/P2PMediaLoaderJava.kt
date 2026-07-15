package com.novage.p2pml.api.java

import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.P2PMediaLoader
import com.novage.p2pml.api.config.DynamicCoreConfig
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.playback.PlaybackProvider
import java.lang.AutoCloseable
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.future.future

class P2PMediaLoaderJava(private val loader: P2PMediaLoader) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Subscribes to P2P engine events.
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
    fun addListener(listener: P2PEventListener): AutoCloseable {
        val jobs = listOf(
            loader.p2pEvents.onSegmentLoaded.onEach(listener::onSegmentLoaded).launchIn(scope),
            loader.p2pEvents.onSegmentStart.onEach(listener::onSegmentStart).launchIn(scope),
            loader.p2pEvents.onSegmentError.onEach(listener::onSegmentError).launchIn(scope),
            loader.p2pEvents.onSegmentAbort.onEach(listener::onSegmentAbort).launchIn(scope),
            loader.p2pEvents.onPeerConnect.onEach(listener::onPeerConnect).launchIn(scope),
            loader.p2pEvents.onPeerConnectError.onEach(listener::onPeerConnectError).launchIn(scope),
            loader.p2pEvents.onPeerClose.onEach(listener::onPeerClose).launchIn(scope),
            loader.p2pEvents.onPeerError.onEach(listener::onPeerError).launchIn(scope),
            loader.p2pEvents.onPeerWarning.onEach(listener::onPeerWarning).launchIn(scope),
            loader.p2pEvents.onChunkDownloaded.onEach(listener::onChunkDownloaded).launchIn(scope),
            loader.p2pEvents.onChunkUploaded.onEach(listener::onChunkUploaded).launchIn(scope),
            loader.p2pEvents.onTrackerError.onEach(listener::onTrackerError).launchIn(scope),
            loader.p2pEvents.onTrackerWarning.onEach(listener::onTrackerWarning).launchIn(scope)
        )
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

    fun initialize(provider: PlaybackProvider): CompletableFuture<Void?> = scope.future {
        loader.initialize(provider)
        null
    }

    fun initialize(exoPlayer: ExoPlayer): CompletableFuture<Void?> = scope.future {
        loader.initialize(exoPlayer)
        null
    }

    /** See [P2PMediaLoader.createPlaybackUrl]: one active stream per loader. */
    @Throws(P2PMediaLoaderException::class)
    fun createPlaybackUrl(manifestUrl: String): String = loader.createPlaybackUrl(manifestUrl)

    /** See [P2PMediaLoader.applyDynamicConfig]: partial patch; pre-initialization calls are last-wins. */
    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) = loader.applyDynamicConfig(dynamicCoreConfig)

    fun release() {
        scope.cancel()
        loader.release()
    }
}
