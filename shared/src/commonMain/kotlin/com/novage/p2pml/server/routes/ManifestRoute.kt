package com.novage.p2pml.server.routes

import com.novage.p2pml.server.services.ManifestService
import com.novage.p2pml.server.utils.fetchManifest
import com.novage.p2pml.utils.CoreLogger
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private val logger = CoreLogger("ManifestRoute")

internal fun Route.registerManifestRoute(httpClient: HttpClient, manifestService: ManifestService) {
    get("/${RoutePaths.MANIFEST}/{manifestUrl}") {
        val manifestUrl = call.parameters["manifestUrl"]

        logger.d { "Incoming request for manifest: $manifestUrl" }

        if (manifestUrl.isNullOrBlank()) {
            logger.w { "Request rejected: Missing manifest URL parameter" }
            call.respondText("Missing manifest URL", status = HttpStatusCode.BadRequest)
            return@get
        }

        try {
            val fetchResult = httpClient.fetchManifest(call, manifestUrl)

            logger.d { "Successfully fetched upstream manifest. Processing..." }

            val modifiedManifest =
                manifestService.processManifest(
                    fetchResult.responseUrl,
                    fetchResult.manifestContent
                )

            call.respondText(modifiedManifest, ContentType.parse("application/vnd.apple.mpegurl"))
        } catch (ex: Exception) {
            logger.e(ex) { "Error processing manifest request for: $manifestUrl" }

            call.respondText(
                "Error processing manifest",
                status = HttpStatusCode.InternalServerError
            )
        }
    }
}
