package com.novage.p2pml.webview

actual class WebViewManagerImpl : WebViewManager {
    override fun loadUrl(url: String) {
        TODO("Not yet implemented")
    }

    override fun applyDynamicConfig(config: String) {
        TODO("Not yet implemented")
    }

    override suspend fun setManifestUrl(manifestUrl: String) {
        TODO("Not yet implemented")
    }

    override suspend fun sendAllStreams(streamsJson: String) {
        TODO("Not yet implemented")
    }

    override suspend fun sendStream(streamJson: String) {
        TODO("Not yet implemented")
    }

    override suspend fun requestSegmentBytes(segmentUrl: String) {
        TODO("Not yet implemented")
    }

    override suspend fun initCoreEngine(coreConfigJson: String) {
        TODO("Not yet implemented")
    }
}
