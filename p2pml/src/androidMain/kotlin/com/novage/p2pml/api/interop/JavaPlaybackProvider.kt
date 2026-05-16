package com.novage.p2pml.api.interop

import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.PlaybackInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
}
