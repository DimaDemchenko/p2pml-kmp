package com.novage.p2pml.server

import com.novage.p2pml.parser.HlsManifestParser
import com.novage.p2pml.parser.decodeBase64Url
import com.novage.p2pml.resources.INDEX_HTML
import com.novage.p2pml.resources.P2PML_CORE_JS
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.toByteArray

data class ManifestFetchResult(val manifestContent: String, val responseUrl: String)

internal fun Application.configureRoutes(
    client: HttpClient,
    manifestHandler: ManifestHandler,
    manifestParser: HlsManifestParser,
    segmentHandler: SegmentHandler,
) {
    routing {
        registerManifestRoute(client, manifestHandler)
        registerSegmentRoute(client, segmentHandler, manifestParser)
        registerSegmentReceiveRoute(segmentHandler)
        registerStaticRoute()
        registerStaticJsRoute()
    }
}

internal fun Route.registerSegmentReceiveRoute(segmentHandler: SegmentHandler) {
    post("/segment-upload") {
        val segmentId =
            call.request.queryParameters["segmentId"]
                ?: return@post call.respondText(
                    "Missing segment ID",
                    status = HttpStatusCode.BadRequest,
                )
        val error = call.request.queryParameters["error"]

        if (error != null) {
            val deferredSegment =
                segmentHandler.getSegmentRequest(segmentId)
                    ?: run {
                        call.respond(HttpStatusCode.NotFound)
                        return@post
                    }

            if (error.contains("aborted")) {
                deferredSegment.completeExceptionally(
                    SegmentAbortedException("Segment aborted - $segmentId")
                )
            } else {
                deferredSegment.completeExceptionally(
                    Exception("Error processing segment - $segmentId - $error")
                )
                segmentHandler.removeSegmentRequest(segmentId)
            }

            call.respond(HttpStatusCode.OK)
            return@post
        }

        val segmentBytes = call.receiveChannel().toByteArray()
        println("↓↓↓↓ Received segment bytes for $segmentId")
        segmentHandler.completeSegmentRequest(segmentId, segmentBytes)

        call.respond(HttpStatusCode.OK)
    }
}

internal fun Route.registerManifestRoute(httpClient: HttpClient, manifestHandler: ManifestHandler) {
    get("/manifest/{manifestUrl}") {
        val manifestUrl = call.parameters["manifestUrl"]

        if (manifestUrl.isNullOrBlank()) {
            call.respondText("Missing manifest URL", status = HttpStatusCode.BadRequest)
            return@get
        }

        try {
            val fetchResult = fetchManifest(httpClient, call, manifestUrl)
            val modifiedManifest =
                manifestHandler.getModifiedManifest(
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

internal fun Route.registerSegmentRoute(
    httpClient: HttpClient,
    segmentHandler: SegmentHandler,
    parser: HlsManifestParser,
) {
    get("/segment/{segmentUrl}") {
        val encodedSegmentUrl = call.parameters["segmentUrl"]

        if (encodedSegmentUrl.isNullOrBlank()) {
            call.respondText("Missing segment URL", status = HttpStatusCode.BadRequest)
            return@get
        }
        val segmentUrl = decodeBase64Url(encodedSegmentUrl)
        val byteRange = call.request.headers[HttpHeaders.Range]

        try {
            val isCurrentSegment = parser.isCurrentSegment(segmentUrl)

            if (!isCurrentSegment) {
                val fetchedSegmentBytes = fetchSegment(httpClient, call, segmentUrl)
                respondWithSegmentBytes(call, fetchedSegmentBytes, byteRange)
                return@get
            }

            println("↑↑↑↑ Requesting segment from P2P network $segmentUrl")
            val deferredSegmentBytes =
                segmentHandler.registerSegmentRequest(segmentUrl)
                    ?: throw SegmentAlreadyRequested("Segment already requested")

            val segmentBytes = deferredSegmentBytes.await()
            respondWithSegmentBytes(call, segmentBytes, byteRange)
        } catch (ex: Exception) {
            if (ex is SegmentAbortedException) {
                println("Segment aborted: ${ex.message}")
                call.respondText("Segment aborted", status = HttpStatusCode.RequestTimeout)
                return@get
            } else if (ex is SegmentAlreadyRequested) {
                println("Segment already requested: ${ex.message}")
                call.respondText("Segment already requested", status = HttpStatusCode.Conflict)
                return@get
            }

            println("Error processing segment: ${ex.message} | Fetching through HTTP")
            val fetchedSegmentBytes = fetchSegment(httpClient, call, segmentUrl)
            respondWithSegmentBytes(call, fetchedSegmentBytes, byteRange)
        }
    }
}

internal fun Route.registerStaticRoute() {
    get("/static/") { call.respondText(INDEX_HTML, ContentType.Text.Html) }
}

internal fun Route.registerStaticJsRoute() {
    get("/static/js") { call.respondText(P2PML_CORE_JS, ContentType.Application.JavaScript) }
}
