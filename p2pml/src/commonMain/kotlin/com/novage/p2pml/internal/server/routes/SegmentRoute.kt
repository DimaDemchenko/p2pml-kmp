package com.novage.p2pml.internal.server.routes

import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.parser.encoding.decodeFromUrlSafeBase64
import com.novage.p2pml.internal.server.exceptions.SegmentAbortedException
import com.novage.p2pml.internal.server.exceptions.SegmentProcessingException
import com.novage.p2pml.internal.server.exceptions.SegmentReplacedException
import com.novage.p2pml.internal.server.exceptions.TooManyRetriesException
import com.novage.p2pml.internal.server.services.SegmentPayload
import com.novage.p2pml.internal.server.services.SegmentService
import com.novage.p2pml.internal.server.utils.respondFallback
import com.novage.p2pml.internal.server.utils.respondVideoSegmentStream
import com.novage.p2pml.internal.utils.CoreLogger
import com.novage.p2pml.internal.utils.RuntimeErrorDispatcher
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private val logger = CoreLogger("SegmentRoute")
private const val P2P_ENGINE_TIMEOUT_MS = 30_000L

internal fun Route.registerSegmentRoutes(
    httpClient: HttpClient,
    segmentService: SegmentService,
    parser: HlsManifestManager,
    errorDispatcher: RuntimeErrorDispatcher
) {
    segmentDownloadRoute(httpClient, segmentService, parser, errorDispatcher)
    segmentUploadRoute(segmentService)
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun Route.segmentDownloadRoute(
    httpClient: HttpClient,
    segmentService: SegmentService,
    parser: HlsManifestManager,
    errorDispatcher: RuntimeErrorDispatcher
) {
    get("/${RoutePaths.SEGMENT}/{segmentUrl}") {
        val encodedSegmentUrl = call.parameters["segmentUrl"]

        if (encodedSegmentUrl.isNullOrBlank()) {
            call.respondText("Missing segment URL", status = HttpStatusCode.BadRequest)
            return@get
        }

        val segmentUrl = decodeFromUrlSafeBase64(encodedSegmentUrl)
        val byteRange = call.request.headers[HttpHeaders.Range]

        logger.d { "Player requested segment: $segmentUrl (Range: $byteRange)" }

        if (!parser.isCurrentSegment(segmentUrl)) {
            logger.d { "Segment not tracked by P2P. Passthrough to HTTP: $segmentUrl" }
            call.respondFallback(httpClient, segmentUrl, errorDispatcher, byteRange)
            return@get
        }

        var deferred: CompletableDeferred<SegmentPayload>? = null

        try {
            deferred = segmentService.createOrReplaceRequest(segmentUrl)

            logger.d { "Waiting for segment data from p2p engine for: $segmentUrl" }

            val payload = withTimeout(P2P_ENGINE_TIMEOUT_MS) {
                deferred.await()
            }

            logger.d { "Serving stream to Player for: $segmentUrl" }
            call.respondVideoSegmentStream(payload, byteRange)
        } catch (_: TimeoutCancellationException) {
            logger.w { "P2P Engine timed out providing segment. Falling back to HTTP." }
            call.respondFallback(httpClient, segmentUrl, errorDispatcher, byteRange)
        } catch (_: SegmentReplacedException) {
            logger.i { "Request replaced. Terminating old request." }
            call.respond(HttpStatusCode.RequestTimeout)
        } catch (_: TooManyRetriesException) {
            logger.w { "Max retries hit for P2P. Falling back to HTTP." }
            call.respondFallback(httpClient, segmentUrl, errorDispatcher, byteRange)
        } catch (e: SegmentProcessingException) {
            logger.e(e) { "P2P Error: ${e.message}. Falling back to HTTP." }
            call.respondFallback(httpClient, segmentUrl, errorDispatcher, byteRange)
        } catch (_: SegmentAbortedException) {
            logger.w { "P2P Engine aborted segment (Abandoned by ABR/Seek). Terminating cleanly." }
            call.respond(HttpStatusCode.RequestTimeout)
        } finally {
            if (deferred?.isActive == true) {
                logger.d { "Cleaning up active request." }
                segmentService.cancelRequest(segmentUrl, deferred)
            }
            if (deferred?.isCompleted == true) {
                runCatching { deferred.getCompleted().channel.cancel(null) }
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
            if (segmentService.failRequest(segmentId, error)) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
            return@post
        }

        val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val channel = ByteChannel()

        logger.i { "Receiving segment stream for $segmentId (Size: ${contentLength ?: "Unknown"} bytes)" }
        segmentService.completeRequest(segmentId, SegmentPayload(channel, contentLength))

        if (channel.isClosedForWrite) {
            logger.i {
                "Segment $segmentId was abandoned by the player. " +
                    "Aborting incoming JS upload to save resources."
            }
            call.receiveChannel().cancel(null)
            call.respond(HttpStatusCode.Accepted)
            return@post
        }

        runCatching {
            call.receiveChannel().copyAndClose(channel)
        }.onFailure { e ->
            call.receiveChannel().cancel(e)
            channel.cancel(e)
            throw e
        }
        call.respond(HttpStatusCode.OK)
    }
}
