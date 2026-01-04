package com.novage.p2pml.server.config

import com.novage.p2pml.server.routes.RoutePaths

internal class LocalUrlFactory {
    private var port: Int = -1

    fun setPort(newPort: Int) {
        port = newPort
    }

    private fun getBaseUrl(): String {
        if (port == -1) throw IllegalStateException("P2P Server not ready (Port not set)")
        return "http://127.0.0.1:$port"
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