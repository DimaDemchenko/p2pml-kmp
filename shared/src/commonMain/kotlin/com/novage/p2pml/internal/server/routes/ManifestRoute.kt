package com.novage.p2pml.internal.server.routes

import com.novage.p2pml.MediaLoaderErrorType
import com.novage.p2pml.internal.server.services.ManifestService
import com.novage.p2pml.internal.server.utils.fetchManifest
import com.novage.p2pml.internal.utils.CoreLogger
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.io.IOException

private val logger = CoreLogger("ManifestRoute")

internal fun Route.registerManifestRoute(
    httpClient: HttpClient,
    manifestService: ManifestService,
    onError: (MediaLoaderErrorType, String) -> Unit
) {
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
            logger.e { "Network error fetching manifest: ${e.message}" }
            onError(MediaLoaderErrorType.MANIFEST_LOAD_ERROR, "Failed to fetch manifest: $manifestUrl - ${e.message}")
            call.respondText("Upstream manifest unreachable", status = HttpStatusCode.BadGateway)
        } catch (e: IllegalStateException) {
            logger.e(e) { "Error processing manifest: ${e.message}" }
            onError(
                MediaLoaderErrorType.MANIFEST_PARSE_ERROR,
                "Failed to process manifest: $manifestUrl - ${e.message}"
            )
            call.respondText("Invalid manifest state", status = HttpStatusCode.InternalServerError)
        }
    }
}
