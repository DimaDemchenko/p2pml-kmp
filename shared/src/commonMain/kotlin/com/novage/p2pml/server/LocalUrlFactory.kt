package com.novage.p2pml.server

class LocalUrlFactory(private val config: ServerConfig) {

    private fun getBaseUrl(): String {
        if (!config.isReady) throw IllegalStateException("❌ P2P Server not ready")
        return "http://127.0.0.1:${config.port}"
    }

    fun buildManifestUrl(encodedUrl: String): String {
        return "${getBaseUrl()}/${RouteConfig.MANIFEST_ROUTE}/$encodedUrl"
    }

    fun buildSegmentUrl(encodedUrl: String): String {
        return "${getBaseUrl()}/${RouteConfig.SEGMENT_ROUTE}/$encodedUrl"
    }

    fun buildStaticPageUrl(): String {
        return "${getBaseUrl()}/${RouteConfig.STATIC_ROUTE}/"
    }

    fun buildUploadUrl(): String {
        return "${getBaseUrl()}/${RouteConfig.UPLOAD_ROUTE}"
    }
}