package com.novage.p2pml

import com.novage.p2pml.api.config.CoreConfig
import com.novage.p2pml.api.config.DynamicCoreConfig
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.logging.P2PLogging
import com.novage.p2pml.api.playback.PlaybackProvider
import com.novage.p2pml.internal.core.P2PMediaLoaderCore
import com.novage.p2pml.internal.playback.AVPlayerPlaybackProvider
import com.novage.p2pml.internal.webview.IosWebViewFactory
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import platform.AVFoundation.AVPlayer

/**
 * Entry point for P2P-accelerated media streaming on iOS.
 *
 * This class is **single-use**: after [release] is called, this instance cannot be re-initialized.
 * Create a new [P2PMediaLoader] instance if you need to restart P2P streaming.
 *
 * @param coreConfig Engine configuration. Defaults are used when omitted.
 * @param customEngineUrl URL to a custom-hosted engine page. Uses the bundled asset by default.
 *   Custom pages do not include the bundled page's log bridge, so engine console output and
 *   uncaught JS errors are not forwarded to the native log unless the page replicates it.
 *   The page must implement this library version's bridge contract: signal readiness with an
 *   `onWebViewLoaded` message (otherwise loading fails with `ENGINE_LOAD_TIMEOUT`) and acknowledge
 *   `initP2P` with `onCoreInitialized`/`onCoreInitFailed` (otherwise initialization fails with
 *   `ENGINE_INIT_FAILED` once the ack times out). Build the page from this repository's
 *   `p2pml/src/assets` to stay in sync.
 */
class P2PMediaLoader(coreConfig: CoreConfig = CoreConfig(), customEngineUrl: String? = null) {

    private val core = P2PMediaLoaderCore(coreConfig, customEngineUrl)

    @Volatile
    private var defaultProvider: AVPlayerPlaybackProvider? = null

    val p2pEvents get() = core.p2pEvents

    /**
     * Observable, latched loader state. On [com.novage.p2pml.api.state.P2PMediaLoaderStatus.FAILED],
     * the local proxy is gone — fall back to the origin URL.
     */
    val state get() = core.state

    /**
     * Builds the local proxy URL for [manifestUrl] to hand to the player instead of the origin URL.
     *
     * One active stream per loader: when the player starts fetching a manifest that does not belong
     * to the currently tracked stream, all P2P state for the previous stream is reset. To play
     * several streams concurrently, use a separate [P2PMediaLoader] instance per stream.
     *
     * @throws P2PMediaLoaderException if the loader is not initialized or already released.
     */
    @Throws(P2PMediaLoaderException::class)
    fun createPlaybackUrl(manifestUrl: String) = core.createPlaybackUrl(manifestUrl)

    /**
     * Applies [dynamicCoreConfig] to the running engine as a partial patch: only explicitly set
     * properties are overridden, and successive calls accumulate in the engine.
     *
     * Calls made before initialization completes are cached and applied once the loader becomes
     * active; only the most recent pre-initialization config is kept — earlier ones are dropped,
     * not merged. Calls after the loader has failed or been released are ignored.
     */
    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) = core.applyDynamicConfig(dynamicCoreConfig)

    /**
     * Stops P2P streaming and tears down the local proxy, engine WebView and HTTP client.
     * Returns immediately; resources are freed asynchronously. Idempotent, non-suspending and
     * safe to call from `deinit`.
     */
    fun release() {
        core.release()
        defaultProvider?.release()
        defaultProvider = null
    }

    companion object {
        /**
         * Lowers [com.novage.p2pml.api.logging.P2PLogging.minLevel] to DEBUG for full diagnostics.
         * Call before [initialize] so the internal WebView is also created inspectable.
         * Debug output includes manifest and segment URLs, which may carry signed query parameters.
         */
        fun enableLogging() = P2PLogging.enableLogging()

        /**
         * Restores the default log verbosity (WARN and above). To silence the library entirely,
         * set [com.novage.p2pml.api.logging.P2PLogging.sink] to null instead.
         */
        fun disableLogging() = P2PLogging.disableLogging()
    }

    /**
     * Initializes and starts P2P media streaming components.
     *
     * The media loader automatically creates and manages the lifecycle of the internal playback
     * provider for [AVPlayer], and will release it when [release] is called on this loader.
     *
     * @param player AVPlayer instance for media playback
     * @throws P2PMediaLoaderException if initialization or startup fails
     * @throws CancellationException if the coroutine is cancelled (e.g. the enclosing Swift
     *   `Task` is cancelled). Cancellation is terminal: the loader ends up released and cannot
     *   be re-initialized — create a new instance to retry.
     */
    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    suspend fun initialize(player: AVPlayer) {
        val provider = AVPlayerPlaybackProvider(player)
        try {
            core.initialize(provider, IosWebViewFactory())
        } catch (e: P2PMediaLoaderException) {
            provider.release()
            throw e
        } catch (e: CancellationException) {
            provider.release()
            throw e
        }

        defaultProvider = provider
    }

    /**
     * Initializes and starts P2P media streaming components with a custom playback provider.
     *
     * **Important:** When passing a custom [PlaybackProvider], the caller retains ownership of
     * its lifecycle and is responsible for calling [PlaybackProvider.release] when the provider
     * is no longer needed. The media loader will not release custom providers automatically.
     *
     * @param provider Custom Playback Provider
     * @throws P2PMediaLoaderException if initialization or startup fails
     * @throws CancellationException if the coroutine is cancelled (e.g. the enclosing Swift
     *   `Task` is cancelled). Cancellation is terminal: the loader ends up released and cannot
     *   be re-initialized — create a new instance to retry.
     */
    @Throws(P2PMediaLoaderException::class, CancellationException::class)
    suspend fun initialize(provider: PlaybackProvider) {
        core.initialize(provider, IosWebViewFactory())
    }
}
