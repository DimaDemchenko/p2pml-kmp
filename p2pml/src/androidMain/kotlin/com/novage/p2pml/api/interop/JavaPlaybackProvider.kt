package com.novage.p2pml.api.interop

import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.api.models.PlaylistSnapshot
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await

/**
 * A Java-friendly adapter for [PlaybackProvider].
 * Pure Java consumers should extend this class to implement custom video player integrations.
 */
abstract class JavaPlaybackProvider : PlaybackProvider {

    private val _playbackUpdates = MutableStateFlow(PlaybackInfo(0.0, 1.0f))
    final override val playbackUpdates: StateFlow<PlaybackInfo> = _playbackUpdates.asStateFlow()

    /**
     * Java consumers must call this method whenever their custom player's time or speed changes.
     */
    protected fun pushPlaybackUpdate(info: PlaybackInfo) {
        _playbackUpdates.value = info
    }

    final override suspend fun getAbsolutePlaybackPosition(snapshot: PlaylistSnapshot): Double {
        return getAbsolutePlaybackPositionAsync(snapshot).await()
    }

    final override suspend fun clearState() {
        clearStateAsync().await()
    }

    /**
     * Maps sliding-window offsets asynchronously (if required by the custom player).
     * @param snapshot The current playlist snapshot to map against.
     * @return A CompletableFuture resolving to the absolute anchor epoch time.
     */
    abstract fun getAbsolutePlaybackPositionAsync(snapshot: PlaylistSnapshot): CompletableFuture<Double>

    /**
     * Flushes cached segments and snapshot maps during mid-session manifest resets.
     * @return A CompletableFuture signaling completion.
     */
    abstract fun clearStateAsync(): CompletableFuture<Void?>

    /**
     * Completely tears down native observers and background threads on session death.
     */
    override fun release() {}
}
