package com.novage.p2pml.server.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import p2pmedialoadermobile.shared.generated.resources.Res
import kotlin.text.ifEmpty

internal fun Route.registerWebAssets() {
    get("/${RoutePaths.STATIC}/{path...}") {
        val path = call.parameters.getAll("path")?.joinToString("/")?.ifEmpty { null }
            ?: "index.html"

        try {
            val bytes = Res.readBytes("files/$path")

            val contentType = when {
                path.endsWith(".js") -> ContentType.Application.JavaScript
                path.endsWith(".html") -> ContentType.Text.Html
                else -> ContentType.Application.OctetStream
            }

            call.respondBytes(bytes, contentType)
        } catch (_: Exception) {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}