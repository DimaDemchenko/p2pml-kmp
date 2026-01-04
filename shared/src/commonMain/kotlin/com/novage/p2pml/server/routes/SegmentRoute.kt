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
import com.novage.p2pml.utils.CoreLogger
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

private val logger = CoreLogger("SegmentRoute")

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

        logger.d { "Player requested segment: $segmentUrl (Range: $byteRange)" }

        if (!parser.isCurrentSegment(segmentUrl)) {
            logger.d { "Segment not tracked by P2P. Passthrough to HTTP: $segmentUrl" }
            val fetchedSegmentBytes = httpClient.fetchSegment(call, segmentUrl)

            call.respondVideoSegment(fetchedSegmentBytes, byteRange)
            return@get
        }

        var deferred: CompletableDeferred<ByteArray>? = null

        try {
            deferred = segmentService.createOrReplaceRequest(segmentUrl)

            logger.d { "Waiting for segment data from p2p engine for: $segmentUrl" }
            val bytes = deferred.await()
            logger.d { "Serving ${bytes.size} bytes to Player for: $segmentUrl" }

            call.respondVideoSegment(bytes, byteRange)
        } catch (_: SegmentReplacedException) {
            logger.i { "Request replaced. Terminating old request." }
            call.respond(HttpStatusCode.RequestTimeout)

        } catch (_: TooManyRetriesException) {
            logger.w { "Max retries hit for P2P. Falling back to HTTP." }
            call.respondFallback(httpClient, segmentUrl, byteRange)

        } catch (e: Exception) {
            logger.e(e) { "P2P Error: ${e.message}. Falling back to HTTP." }
            call.respondFallback(httpClient, segmentUrl, byteRange)

        } finally {
            if (deferred?.isActive == true) {
                logger.d { "Cleaning up active request." }
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
                    logger.w { "Received error for unknown segment ID: $segmentId" }
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }

            if (error.contains("aborted")) {
                logger.i { "Segment upload aborted: $segmentId" }
                deferredSegment.completeExceptionally(
                    SegmentAbortedException("Segment aborted - $segmentId")
                )
            } else {
                logger.w { "Error processing segment: $segmentId - $error" }
                deferredSegment.completeExceptionally(
                    Exception("Error processing segment - $segmentId - $error")
                )
                segmentService.removeRequest(segmentId)
            }

            call.respond(HttpStatusCode.OK)
            return@post
        }

        val segmentBytes = call.receiveChannel().toByteArray()

        logger.i { "Received segment bytes for $segmentId (Size: ${segmentBytes.size} bytes)" }

        segmentService.completeRequest(segmentId, segmentBytes)

        call.respond(HttpStatusCode.OK)
    }
}