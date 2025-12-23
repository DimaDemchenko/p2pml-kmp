package com.novage.p2pml.server

import com.novage.p2pml.parser.HlsManifestParser
import com.novage.p2pml.parser.decodeBase64Url
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.CompletableDeferred
import p2pmedialoadermobile.shared.generated.resources.Res

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
        registerWebAssets()
    }
}

internal fun Route.registerSegmentReceiveRoute(segmentHandler: SegmentHandler) {
    post("/${RouteConfig.UPLOAD_ROUTE}") {
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
    get("/${RouteConfig.MANIFEST_ROUTE}/{manifestUrl}") {
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
    get("/${RouteConfig.SEGMENT_ROUTE}/{segmentUrl}") {
        val encodedSegmentUrl = call.parameters["segmentUrl"]

        if (encodedSegmentUrl.isNullOrBlank()) {
            call.respondText("Missing segment URL", status = HttpStatusCode.BadRequest)
            return@get
        }

        val segmentUrl = decodeBase64Url(encodedSegmentUrl)
        val byteRange = call.request.headers[HttpHeaders.Range]

        if (!parser.isCurrentSegment(segmentUrl)) {
            val fetchedSegmentBytes = fetchSegment(httpClient, call, segmentUrl)
            respondWithSegmentBytes(call, fetchedSegmentBytes, byteRange)
            return@get
        }

        var deferred: CompletableDeferred<ByteArray>? = null

        try {
            deferred = segmentHandler.createOrReplaceRequest(segmentUrl)

            val bytes = deferred.await()
            respondWithSegmentBytes(call, bytes, byteRange)
        } catch (_: SegmentReplacedException) {
            println("♻️ Old request replaced. Terminating.")
            call.respond(HttpStatusCode.RequestTimeout)
        } catch (_: TooManyRetriesException) {
            println("🚫 Max retries hit. Fallback to HTTP.")
            respondFallback(httpClient, call, segmentUrl, byteRange)
        } catch (e: Exception) {
            println("❌ P2P Error: ${e.message}. Fallback.")
            respondFallback(httpClient, call, segmentUrl, byteRange)
        } finally {
            if (deferred != null) {
                segmentHandler.cancelRequest(segmentUrl, deferred)
            }
        }
    }
}

internal fun Route.registerWebAssets() {
    get("/${RouteConfig.STATIC_ROUTE}/{path...}") {
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