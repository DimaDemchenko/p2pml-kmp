package com.novage.p2pml.internal.server.services

import com.novage.p2pml.api.config.CoreConfig
import com.novage.p2pml.api.config.DynamicCoreConfig
import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.parser.hls.Stream
import com.novage.p2pml.internal.parser.hls.UpdateStreamParams

/**
 * Records every engine interaction in call order, so tests can assert not just that the
 * services drive the engine, but in which sequence — the part of the contract the JS bridge
 * depends on.
 */
internal class RecordingP2PEngine : P2PEngine {
    val calls = mutableListOf<String>()
    val requestedSegments = mutableListOf<String>()

    override suspend fun loadUrlAndWait(url: String) = Unit
    override suspend fun initCoreEngineAndWait(coreConfig: CoreConfig, uploadUrl: String) = Unit
    override fun destroy() = Unit

    override fun requestSegmentBytes(segmentUrl: String) {
        requestedSegments += segmentUrl
        calls += "requestSegmentBytes"
    }

    override fun sendStream(stream: UpdateStreamParams) {
        calls += "sendStream:+${stream.addSegments.size}-${stream.removeSegmentsIds.size}"
    }

    override fun sendAllStreams(streams: List<Stream>) {
        calls += "sendAllStreams:${streams.size}"
    }

    override fun setManifestUrl(manifestUrl: String) {
        calls += "setManifestUrl"
    }

    override fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig) {
        calls += "applyDynamicConfig"
    }

    override fun subscribeToP2PEvent(eventName: String) = Unit
    override fun unsubscribeFromP2PEvent(eventName: String) = Unit
    override fun updatePlaybackInfo(positionSec: Double, speed: Float) = Unit
}
