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
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

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

            val modifiedManifest = manifestService.processManifest(
                fetchResult.responseUrl,
                fetchResult.manifestContent
            )

            call.respondText(modifiedManifest, ContentType.parse("application/vnd.apple.mpegurl"))
        } catch (e: IOException) {
            logger.e(e) { "Network error fetching manifest: $manifestUrl" }
            call.respondText("Upstream manifest unreachable", status = HttpStatusCode.BadGateway)
        } catch (e: IllegalStateException) {
            logger.e(e) { "State error processing manifest: $manifestUrl" }
            call.respondText("Invalid manifest state", status = HttpStatusCode.InternalServerError)
        }
    }
}
