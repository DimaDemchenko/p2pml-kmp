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

        val bytes = when (path) {
            P2PAssets.HTML_FILENAME -> P2PAssets.INDEX_HTML_BYTES
            P2PAssets.JS_FILENAME -> P2PAssets.CORE_JS_BYTES
            else -> null
        }

        if (bytes != null) {
            val contentType = when {
                path.endsWith(".js") -> ContentType.Application.JavaScript
                path.endsWith(".html") -> ContentType.Text.Html
                else -> ContentType.Application.OctetStream
            }
            call.respondBytes(bytes, contentType)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
