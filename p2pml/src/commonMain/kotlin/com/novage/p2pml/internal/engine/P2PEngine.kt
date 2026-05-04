package com.novage.p2pml.internal.engine

internal interface P2PEngine {
    suspend fun loadUrlAndWait(url: String)
    fun initCoreEngine(coreConfig: String, uploadUrl: String)
    fun destroy()

    fun requestSegmentBytes(segmentUrl: String)
    fun sendStream(streamJson: String)
    fun sendAllStreams(streamsJson: String)
    fun setManifestUrl(manifestUrl: String)
    fun applyDynamicConfig(dynamicCoreConfig: String)

    fun subscribeToP2PEvent(eventName: String)
    fun unsubscribeFromP2PEvent(eventName: String)

    fun updatePlaybackInfo(json: String)
}
