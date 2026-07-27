package com.novage.p2pml.internal.server.routes

import com.novage.p2pml.generated.P2PAssets
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.registerWebAssets() {
    get("/${RoutePaths.STATIC}/{path...}") {
        val path = call.parameters.getAll("path")?.joinToString("/")?.ifEmpty { null }
            ?: P2PAssets.HTML_FILENAME

        val asset = when (path) {
            P2PAssets.HTML_FILENAME -> P2PAssets.INDEX_HTML_BYTES to ContentType.Text.Html
            P2PAssets.JS_FILENAME -> P2PAssets.CORE_JS_BYTES to ContentType.Application.JavaScript
            else -> null
        }

        if (asset == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val (bytes, contentType) = asset
        call.respondBytes(bytes, contentType)
    }
}
