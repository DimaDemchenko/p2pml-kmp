package com.novage.p2pml.internal.server.routes

import com.novage.p2pml.internal.parser.ManifestParseException
import com.novage.p2pml.internal.server.services.ManifestService
import com.novage.p2pml.internal.server.utils.fetchManifest
import com.novage.p2pml.internal.utils.CoreLogger
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
                requestUrl = manifestUrl,
                responseUrl = fetchResult.responseUrl,
                manifest = fetchResult.manifestContent
            )

            call.respondText(modifiedManifest, ContentType.parse("application/vnd.apple.mpegurl"))
        } catch (e: ResponseException) {
            val status = e.response.status
            logger.w { "Upstream error fetching manifest [$manifestUrl]: $status" }
            call.respondText("Upstream returned $status", status = status)
        } catch (e: IOException) {
            logger.e(e) { "Network error fetching manifest [$manifestUrl]" }
            call.respondText("Upstream manifest unreachable", status = HttpStatusCode.BadGateway)
        } catch (e: ManifestParseException) {
            logger.e(e) { "Upstream returned invalid manifest content [$manifestUrl]" }
            call.respondText("Invalid manifest", status = HttpStatusCode.InternalServerError)
        } catch (e: IllegalStateException) {
            logger.e(e) { "Parser crashed on manifest [$manifestUrl]" }
            call.respondText("Invalid manifest state", status = HttpStatusCode.InternalServerError)
        } catch (e: SerializationException) {
            logger.e(e) { "Serialization crashed on manifest [$manifestUrl]" }
            call.respondText("Invalid manifest serialization", status = HttpStatusCode.InternalServerError)
        }
    }
}
