package com.novage.p2pml.internal.server.routes

import com.novage.p2pml.api.errors.P2PMediaLoaderErrorType
import com.novage.p2pml.internal.server.services.ManifestService
import com.novage.p2pml.internal.server.utils.fetchManifest
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.utils.RuntimeErrorDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

private val logger = CoreLogger("ManifestRoute")

internal fun Route.registerManifestRoute(
    httpClient: HttpClient,
    manifestService: ManifestService,
    errorDispatcher: RuntimeErrorDispatcher
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
        } catch (e: ResponseException) {
            val status = e.response.status
            logger.w { "Upstream error fetching manifest [$manifestUrl]: $status" }

            errorDispatcher.tryEmit(
                P2PMediaLoaderErrorType.MANIFEST_LOAD_ERROR,
                "Manifest load failed (HTTP $status)"
            )
            call.respondText("Upstream returned $status", status = HttpStatusCode.BadGateway)
        } catch (e: IOException) {
            val errorMsg = e.message ?: "Connection lost"
            logger.e { "Network error fetching manifest [$manifestUrl]: $errorMsg" }

            errorDispatcher.tryEmit(
                P2PMediaLoaderErrorType.MANIFEST_LOAD_ERROR,
                "Failed to download manifest. Host unreachable or connection lost."
            )
            call.respondText("Upstream manifest unreachable", status = HttpStatusCode.BadGateway)
        } catch (e: IllegalStateException) {
            logger.e(e) { "Parser crashed on manifest [$manifestUrl]" }

            errorDispatcher.tryEmit(
                P2PMediaLoaderErrorType.MANIFEST_PARSE_ERROR,
                "Invalid manifest format. Parser failed: ${e.message}"
            )
            call.respondText("Invalid manifest state", status = HttpStatusCode.InternalServerError)
        } catch (e: SerializationException) {
            logger.e(e) { "Serialization crashed on manifest [$manifestUrl]" }

            errorDispatcher.tryEmit(
                P2PMediaLoaderErrorType.MANIFEST_PARSE_ERROR,
                "Manifest serialization failed: ${e.message}"
            )
            call.respondText("Invalid manifest serialization", status = HttpStatusCode.InternalServerError)
        }
    }
}
