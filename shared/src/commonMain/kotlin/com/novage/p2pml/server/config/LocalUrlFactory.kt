package com.novage.p2pml.server.config

import com.novage.p2pml.server.routes.RoutePaths

internal class LocalUrlFactory {
    private var port: Int = -1

    fun setPort(newPort: Int) {
        port = newPort
    }

    private fun getBaseUrl(): String {
        check(port != -1) { "P2P Server not ready" }
        return "http://127.0.0.1:$port"
    }

    fun buildManifestUrl(encodedUrl: String): String = "${getBaseUrl()}/${RoutePaths.MANIFEST}/$encodedUrl"

    fun buildSegmentUrl(encodedUrl: String): String = "${getBaseUrl()}/${RoutePaths.SEGMENT}/$encodedUrl"

    fun buildStaticPageUrl(): String = "${getBaseUrl()}/${RoutePaths.STATIC}/"

    fun buildUploadUrl(): String = "${getBaseUrl()}/${RoutePaths.SEGMENT_UPLOAD}"
}
