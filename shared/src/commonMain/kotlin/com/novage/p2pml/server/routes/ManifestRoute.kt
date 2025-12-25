package com.novage.p2pml.server.routes

import com.novage.p2pml.server.services.ManifestService
import com.novage.p2pml.server.utils.fetchManifest
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.registerManifestRoute(httpClient: HttpClient, manifestService: ManifestService) {
    get("/${RoutePaths.MANIFEST}/{manifestUrl}") {
        val manifestUrl = call.parameters["manifestUrl"]

        if (manifestUrl.isNullOrBlank()) {
            call.respondText("Missing manifest URL", status = HttpStatusCode.BadRequest)
            return@get
        }

        try {
            val fetchResult = httpClient.fetchManifest(call, manifestUrl)
            val modifiedManifest =
                manifestService.processManifest(
                    fetchResult.responseUrl,
                    fetchResult.manifestContent,
                )

            call.respondText(modifiedManifest, ContentType.parse("application/vnd.apple.mpegurl"))
        } catch (ex: Exception) {
            println("Error processing manifest: ${ex.message}")
            call.respondText(
                "Error processing manifest",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }
}