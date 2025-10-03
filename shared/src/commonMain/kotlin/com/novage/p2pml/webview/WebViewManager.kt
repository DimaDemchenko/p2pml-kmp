package com.novage.p2pml.webview

interface WebViewManager {
    fun loadUrl(url: String)

    fun applyDynamicConfig(config: String)

    suspend fun setManifestUrl(manifestUrl: String)

    suspend fun sendAllStreams(streamsJson: String)

    suspend fun sendStream(streamJson: String)

    suspend fun requestSegmentBytes(segmentUrl: String)

    suspend fun initCoreEngine(coreConfigJson: String)
}

expect class WebViewManagerImpl : WebViewManager
