package com.novage.p2pml.internal.server.routes

import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.parser.encoding.decodeFromUrlSafeBase64
import com.novage.p2pml.internal.server.exceptions.SegmentAbortedException
import com.novage.p2pml.internal.server.exceptions.SegmentProcessingException
import com.novage.p2pml.internal.server.exceptions.SegmentReplacedException
import com.novage.p2pml.internal.server.exceptions.TooManyRetriesException
import com.novage.p2pml.internal.server.services.SegmentPayload
import com.novage.p2pml.internal.server.services.SegmentService
import com.novage.p2pml.internal.server.utils.payloadSatisfiesRequest
import com.novage.p2pml.internal.server.utils.respondFallback
import com.novage.p2pml.internal.server.utils.respondVideoSegmentStream
import com.novage.p2pml.internal.utils.CoreLogger
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private val logger = CoreLogger("SegmentRoute")
private const val P2P_ENGINE_TIMEOUT_MS = 30_000L

internal fun Route.registerSegmentRoutes(
    httpClient: HttpClient,
    segmentService: SegmentService,
    manifestManager: HlsManifestManager
) {
    segmentDownloadRoute(httpClient, segmentService, manifestManager)
    segmentUploadRoute(segmentService)
}

private fun Route.segmentDownloadRoute(
    httpClient: HttpClient,
    segmentService: SegmentService,
    manifestManager: HlsManifestManager
) {
    get("/${RoutePaths.SEGMENT}/{segmentUrl}") {
        val encodedSegmentUrl = call.parameters["segmentUrl"]

        if (encodedSegmentUrl.isNullOrBlank()) {
            call.respondText("Missing segment URL", status = HttpStatusCode.BadRequest)
            return@get
        }

        val segmentUrl = try {
            decodeFromUrlSafeBase64(encodedSegmentUrl)
        } catch (_: IllegalArgumentException) {
            logger.w { "Rejected malformed (non-base64) segment URL parameter." }
            call.respondText("Malformed segment URL", status = HttpStatusCode.BadRequest)
            return@get
        }

        logger.d { "Player requested segment: $segmentUrl" }

        if (!manifestManager.isCurrentSegment(segmentUrl)) {
            logger.d { "Segment not tracked by P2P. Passthrough to HTTP: $segmentUrl" }
            call.respondFallback(httpClient, segmentUrl)
            return@get
        }

        var deferred: CompletableDeferred<SegmentPayload>? = null
        var payload: SegmentPayload? = null

        try {
            deferred = segmentService.createOrReplaceRequest(segmentUrl)

            logger.d { "Waiting for segment data from p2p engine for: $segmentUrl" }

            payload = withTimeout(P2P_ENGINE_TIMEOUT_MS) {
                deferred.await()
            }

            servePayloadOrFallback(call, httpClient, segmentUrl, payload)
        } catch (_: TimeoutCancellationException) {
            logger.w { "P2P Engine timed out providing segment. Falling back to HTTP." }
            call.respondFallback(httpClient, segmentUrl)
        } catch (_: SegmentReplacedException) {
            logger.i { "Request replaced. Terminating old request." }
            call.respond(HttpStatusCode.RequestTimeout)
        } catch (_: TooManyRetriesException) {
            logger.w { "Max retries hit for P2P. Falling back to HTTP." }
            call.respondFallback(httpClient, segmentUrl)
        } catch (e: SegmentProcessingException) {
            logger.e(e) { "P2P Error. Falling back to HTTP." }
            call.respondFallback(httpClient, segmentUrl)
        } catch (_: SegmentAbortedException) {
            logger.w { "P2P Engine aborted segment (Abandoned by ABR/Seek). Terminating cleanly." }
            call.respond(HttpStatusCode.RequestTimeout)
        } finally {
            val deferredToClean = deferred
            val payloadToClean = payload
            // NonCancellable: a player disconnect cancels this handler, and the cleanup must
            // still run. Abandon whenever we did not consume a payload — the engine may have
            // completed the deferred concurrently (e.g. during the fallback after a timeout),
            // and that orphaned channel must be drained or the upload route hangs forever.
            withContext(NonCancellable) {
                if (deferredToClean != null && payloadToClean == null) {
                    segmentService.abandonRequest(segmentUrl, deferredToClean)
                }
                payloadToClean?.channel?.cancel(null)
            }
        }
    }
}

private suspend fun servePayloadOrFallback(
    call: ApplicationCall,
    httpClient: HttpClient,
    segmentUrl: String,
    payload: SegmentPayload
) {
    val rangeHeader = call.request.headers[HttpHeaders.Range]
    if (payloadSatisfiesRequest(rangeHeader, segmentUrl, payload.contentLength)) {
        logger.d { "Serving stream to Player for: $segmentUrl" }
        call.respondVideoSegmentStream(payload, segmentUrl)
    } else {
        logger.i { "P2P payload does not cover requested range '$rangeHeader'. Falling back to HTTP." }
        payload.channel.cancel(null)
        call.respondFallback(httpClient, segmentUrl)
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
        val channel = ByteChannel(autoFlush = true)

        logger.i { "Receiving segment stream for $segmentId (Size: ${contentLength ?: "Unknown"} bytes)" }
        segmentService.completeRequest(segmentId, SegmentPayload(channel, contentLength))

        val incomingChannel = call.receiveChannel()

        if (channel.isClosedForWrite) {
            logger.i {
                "Segment $segmentId was abandoned by the player. " +
                    "Aborting incoming JS upload to save resources."
            }
            incomingChannel.cancel(null)
            call.respond(HttpStatusCode.Accepted)
            return@post
        }

        runCatching {
            incomingChannel.copyAndClose(channel)
        }.onFailure { e ->
            incomingChannel.cancel(e)
            channel.cancel(e)

            logger.i { "Upload of $segmentId aborted mid-stream. Returning Accepted." }
            runCatching { call.respond(HttpStatusCode.Accepted) }
            return@post
        }
        call.respond(HttpStatusCode.OK)
    }
}
