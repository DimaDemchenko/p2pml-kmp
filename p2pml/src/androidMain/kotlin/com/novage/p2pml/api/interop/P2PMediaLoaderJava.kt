package com.novage.p2pml.api.interop

import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.P2PMediaLoader
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.api.models.PlaybackInfo
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
            loader.events.onSegmentLoaded.onEach(listener::onSegmentLoaded).launchIn(scope),
            loader.events.onSegmentStart.onEach(listener::onSegmentStart).launchIn(scope),
            loader.events.onSegmentError.onEach(listener::onSegmentError).launchIn(scope),
            loader.events.onSegmentAbort.onEach(listener::onSegmentAbort).launchIn(scope),
            loader.events.onPeerConnect.onEach(listener::onPeerConnect).launchIn(scope),
            loader.events.onPeerClose.onEach(listener::onPeerClose).launchIn(scope),
            loader.events.onPeerError.onEach(listener::onPeerError).launchIn(scope),
            loader.events.onChunkDownloaded.onEach(listener::onChunkDownloaded).launchIn(scope),
            loader.events.onChunkUploaded.onEach(listener::onChunkUploaded).launchIn(scope),
            loader.events.onTrackerError.onEach(listener::onTrackerError).launchIn(scope),
            loader.events.onTrackerWarning.onEach(listener::onTrackerWarning).launchIn(scope)
        )
        return AutoCloseable { jobs.forEach { it.cancel() } }
    }

    fun start(getPlaybackInfo: () -> PlaybackInfo): CompletableFuture<Void?> = scope.future {
        loader.start(getPlaybackInfo)
        null
    }

    fun start(exoPlayer: ExoPlayer): CompletableFuture<Void?> = scope.future {
        loader.start(exoPlayer)
        null
    }

    fun createPlaybackUrl(manifestUrl: String): String = loader.createPlaybackUrl(manifestUrl)

    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) = loader.applyDynamicConfig(dynamicCoreConfig)

    fun release() {
        scope.cancel()
        loader.release()
    }
}
