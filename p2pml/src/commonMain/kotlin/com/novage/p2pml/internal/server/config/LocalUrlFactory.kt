package com.novage.p2pml.internal.server.config

import com.novage.p2pml.api.errors.P2PMediaLoaderErrorCode
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.internal.server.routes.RoutePaths
import kotlin.concurrent.Volatile

internal class LocalUrlFactory {
    @Volatile
    private var port: Int = -1

    fun setPort(newPort: Int) {
        port = newPort
    }

    private fun getBaseUrl(): String {
        if (port == -1) {
            throw P2PMediaLoaderException(
                P2PMediaLoaderErrorCode.NOT_INITIALIZED,
                "P2P Server not ready"
            )
        }
        return "http://127.0.0.1:$port"
    }

    fun buildManifestUrl(encodedUrl: String): String = "${getBaseUrl()}/${RoutePaths.MANIFEST}/$encodedUrl"

    fun buildSegmentUrl(encodedUrl: String): String = "${getBaseUrl()}/${RoutePaths.SEGMENT}/$encodedUrl"

    fun buildStaticPageUrl(): String = "${getBaseUrl()}/${RoutePaths.STATIC}/"

    fun buildUploadUrl(): String = "${getBaseUrl()}/${RoutePaths.SEGMENT_UPLOAD}"
}
