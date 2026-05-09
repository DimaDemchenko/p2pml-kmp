package com.novage.p2pml.internal.engine

import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.api.models.PlaybackInfo
import com.novage.p2pml.internal.parser.hlsPlaylistParser.Stream
import com.novage.p2pml.internal.parser.hlsPlaylistParser.UpdateStreamParams

internal interface P2PEngine {
    suspend fun loadUrlAndWait(url: String)
    fun initCoreEngine(coreConfig: CoreConfig, uploadUrl: String)
    fun destroy()

    fun requestSegmentBytes(segmentUrl: String)
    fun sendStream(stream: UpdateStreamParams)
    fun sendAllStreams(streams: List<Stream>)
    fun setManifestUrl(manifestUrl: String)
    fun applyDynamicConfig(dynamicCoreConfig: DynamicCoreConfig)

    fun subscribeToP2PEvent(eventName: String)
    fun unsubscribeFromP2PEvent(eventName: String)

    fun updatePlaybackInfo(info: PlaybackInfo)
}
