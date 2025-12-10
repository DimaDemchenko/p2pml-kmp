package com.novage.p2pml.engine

interface P2PEngine {
    fun loadUrl(url: String)
    fun initCoreEngine(coreConfigJson: String)
    fun destroy()

    fun requestSegmentBytes(segmentUrl: String)
    fun sendStream(streamJson: String)
    fun sendAllStreams(streamsJson: String)
    fun setManifestUrl(manifestUrl: String)

    fun subscribeToP2PEvent(eventName: String)
    fun unsubscribeFromP2PEvent(eventName: String)
}