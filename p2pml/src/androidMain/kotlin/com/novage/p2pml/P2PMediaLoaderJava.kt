package com.novage.p2pml

import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.api.models.PlaybackInfo
import java.lang.AutoCloseable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class P2PMediaLoaderJava(private val loader: P2PMediaLoader) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    fun start(getPlaybackInfo: () -> PlaybackInfo) = loader.start(getPlaybackInfo)

    fun start(exoPlayer: ExoPlayer) = loader.start(exoPlayer)

    fun getManifestUrl(manifestUrl: String): String = loader.getManifestUrl(manifestUrl)

    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) = loader.applyDynamicConfig(dynamicCoreConfig)

    fun release() {
        scope.cancel()
        loader.release()
    }
}
