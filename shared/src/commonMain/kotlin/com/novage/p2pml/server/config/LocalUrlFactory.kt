package com.novage.p2pml.server.config

import com.novage.p2pml.server.routes.RoutePaths

class LocalUrlFactory(private val config: ServerConfig) {

    private fun getBaseUrl(): String {
        if (!config.isReady) throw IllegalStateException("❌ P2P Server not ready")
        return "http://127.0.0.1:${config.port}"
    }

    fun buildManifestUrl(encodedUrl: String): String {
        return "${getBaseUrl()}/${RoutePaths.MANIFEST}/$encodedUrl"
    }

    fun buildSegmentUrl(encodedUrl: String): String {
        return "${getBaseUrl()}/${RoutePaths.SEGMENT}/$encodedUrl"
    }

    fun buildStaticPageUrl(): String {
        return "${getBaseUrl()}/${RoutePaths.STATIC}/"
    }

    fun buildUploadUrl(): String {
        return "${getBaseUrl()}/${RoutePaths.SEGMENT_UPLOAD}"
    }
}