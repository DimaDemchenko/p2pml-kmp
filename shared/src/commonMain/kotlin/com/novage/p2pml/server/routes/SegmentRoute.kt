package com.novage.p2pml.server.routes

import com.novage.p2pml.parser.HlsManifestParser
import com.novage.p2pml.parser.encoding.decodeBase64Url
import com.novage.p2pml.server.services.SegmentService
import com.novage.p2pml.server.exceptions.SegmentAbortedException
import com.novage.p2pml.server.exceptions.SegmentReplacedException
import com.novage.p2pml.server.exceptions.TooManyRetriesException
import com.novage.p2pml.server.utils.fetchSegment
import com.novage.p2pml.server.utils.respondFallback
import com.novage.p2pml.server.utils.respondVideoSegment
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.CompletableDeferred

internal fun Route.registerSegmentRoutes(
    httpClient: HttpClient,
    segmentService: SegmentService,
    parser: HlsManifestParser
) {
    segmentDownloadRoute(httpClient, segmentService, parser)
    segmentUploadRoute(segmentService)
}

private fun Route.segmentDownloadRoute(
    httpClient: HttpClient,
    segmentService: SegmentService,
    parser: HlsManifestParser,
) {
    get("/${RoutePaths.SEGMENT}/{segmentUrl}") {
        val encodedSegmentUrl = call.parameters["segmentUrl"]

        if (encodedSegmentUrl.isNullOrBlank()) {
            call.respondText("Missing segment URL", status = HttpStatusCode.BadRequest)
            return@get
        }

        val segmentUrl = decodeBase64Url(encodedSegmentUrl)
        val byteRange = call.request.headers[HttpHeaders.Range]

        if (!parser.isCurrentSegment(segmentUrl)) {
            val fetchedSegmentBytes = httpClient.fetchSegment(call, segmentUrl)

            call.respondVideoSegment(fetchedSegmentBytes, byteRange)
            return@get
        }

        var deferred: CompletableDeferred<ByteArray>? = null

        try {
            deferred = segmentService.createOrReplaceRequest(segmentUrl)

            val bytes = deferred.await()

            call.respondVideoSegment(bytes, byteRange)
        } catch (_: SegmentReplacedException) {
            println("♻️ Old request replaced. Terminating.")
            call.respond(HttpStatusCode.RequestTimeout)
        } catch (_: TooManyRetriesException) {
            println("🚫 Max retries hit. Fallback to HTTP.")

            call.respondFallback(httpClient, segmentUrl, byteRange)

        } catch (e: Exception) {
            println("❌ P2P Error: ${e.message}. Fallback.")

            call.respondFallback(httpClient, segmentUrl, byteRange)

        } finally {
            if (deferred?.isActive == true) {
                segmentService.cancelRequest(segmentUrl, deferred)
            }
        }
    }
}

private fun Route.segmentUploadRoute(segmentService: SegmentService) {
    post("/${RoutePaths.SEGMENT_UPLOAD}") {
        val segmentId = call.request.queryParameters["segmentId"]
            ?: return@post call.respondText("Missing segment ID", status = HttpStatusCode.BadRequest)

        val error = call.request.queryParameters["error"]

        if (error != null) {
            val deferredSegment = segmentService.getPendingRequest(segmentId)
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
                segmentService.removeRequest(segmentId)
            }

            call.respond(HttpStatusCode.OK)
            return@post
        }

        val segmentBytes = call.receiveChannel().toByteArray()
        println("↓↓↓↓ Received segment bytes for $segmentId")
        segmentService.completeRequest(segmentId, segmentBytes)

        call.respond(HttpStatusCode.OK)
    }
}