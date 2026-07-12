package com.novage.p2pml.internal.server.utils

import com.novage.p2pml.internal.parser.byteRangeFromRuntimeId
import com.novage.p2pml.internal.parser.segmentUrlFromRuntimeId
import com.novage.p2pml.internal.server.services.SegmentPayload
import com.novage.p2pml.internal.utils.CoreLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyTo
import kotlinx.io.IOException

private val logger = CoreLogger("NetworkUtils")

private val EXCLUDED_PROXY_HEADERS =
    setOf(
        HttpHeaders.Host,
        HttpHeaders.Connection,
        HttpHeaders.TransferEncoding,
        HttpHeaders.Expect,
        HttpHeaders.Upgrade,
        "Proxy-Connection",
        "Keep-Alive",
        HttpHeaders.AcceptEncoding
    )

internal data class ManifestFetchResult(val manifestContent: String, val responseUrl: String)

internal suspend fun HttpClient.fetchManifest(call: ApplicationCall, manifestUrl: String): ManifestFetchResult {
    val response = this.get(manifestUrl) {
        copyProxyHeaders(call.request.headers)
    }

    return ManifestFetchResult(
        manifestContent = response.bodyAsText(),
        responseUrl = response.request.url.toString()
    )
}

private fun HttpRequestBuilder.copyProxyHeaders(requestHeaders: Headers) {
    requestHeaders.forEach { key, values ->
        if (key !in EXCLUDED_PROXY_HEADERS) {
            values.forEach { value -> headers.append(key, value) }
        }
    }
}

private const val BYTES_PREFIX = "bytes="

internal data class RequestedByteRange(val start: Long, val endInclusive: Long?)

internal fun parseSingleByteRange(rangeHeader: String): RequestedByteRange? {
    if (!rangeHeader.startsWith(BYTES_PREFIX) || rangeHeader.contains(",")) return null

    val parts = rangeHeader.substring(BYTES_PREFIX.length).trim().split('-')
    if (parts.size != 2) return null

    val start = parts[0].trim().toLongOrNull()?.takeIf { it >= 0 } ?: return null
    val endStr = parts[1].trim()

    return when {
        endStr.isEmpty() -> RequestedByteRange(start, endInclusive = null)
        else -> endStr.toLongOrNull()?.takeIf { it >= start }?.let { RequestedByteRange(start, it) }
    }
}

/** Byte span the payload covers: the runtime id's byte range, or [0, contentLength) for whole segments. */
private fun payloadSpan(runtimeId: String, contentLength: Long?): Pair<Long, Long?> {
    val byteRange = byteRangeFromRuntimeId(runtimeId)
    val start = byteRange?.start ?: 0L
    val end = byteRange?.end ?: contentLength?.takeIf { it > 0 }?.let { it - 1 }
    return start to end
}

/**
 * Whether the P2P payload — which always streams exactly the span its runtime id describes —
 * can honestly answer the player's Range request. An unparseable or absent Range means the
 * player expects the whole resource, which only whole-segment payloads provide. On a mismatch
 * (e.g. a mid-segment resume) the caller must fall back to the origin, which honors the
 * forwarded Range header.
 */
internal fun payloadSatisfiesRequest(rangeHeader: String?, runtimeId: String, contentLength: Long?): Boolean {
    val (payloadStart, payloadEnd) = payloadSpan(runtimeId, contentLength)
    val requested = rangeHeader?.let { parseSingleByteRange(it) }
        ?: return payloadStart == 0L

    if (requested.start != payloadStart) return false
    val requestedEnd = requested.endInclusive ?: return true
    return requestedEnd == payloadEnd
}

internal suspend fun ApplicationCall.respondVideoSegmentStream(payload: SegmentPayload, runtimeId: String) {
    val (payloadStart, payloadEnd) = payloadSpan(runtimeId, payload.contentLength)
    val isRangeRequest = request.headers[HttpHeaders.Range]?.let { parseSingleByteRange(it) } != null
    val contentRange = if (isRangeRequest && payloadEnd != null) "bytes $payloadStart-$payloadEnd/*" else null

    respond(
        object : OutgoingContent.ReadChannelContent() {
            override val contentType = ContentType.Application.OctetStream
            override val contentLength = payload.contentLength
            override val status = if (contentRange != null) HttpStatusCode.PartialContent else HttpStatusCode.OK
            override val headers = Headers.build {
                append(HttpHeaders.AcceptRanges, "bytes")
                if (contentRange != null) {
                    append(HttpHeaders.ContentRange, contentRange)
                }
            }
            override fun readFrom(): ByteReadChannel = payload.channel
        }
    )
}

internal suspend fun ApplicationCall.respondFallback(httpClient: HttpClient, segmentUrl: String) {
    val cleanUrl = segmentUrlFromRuntimeId(segmentUrl)
    try {
        httpClient.prepareGet(cleanUrl) {
            copyProxyHeaders(request.headers)
        }.execute { response ->
            val contentType = response.contentType() ?: ContentType.Application.OctetStream
            val contentLength = response.contentLength()
            val contentRange = response.headers[HttpHeaders.ContentRange]

            respond(
                object : OutgoingContent.WriteChannelContent() {
                    override val contentType = contentType
                    override val contentLength = contentLength
                    override val status = response.status
                    override val headers = Headers.build {
                        append(HttpHeaders.AcceptRanges, "bytes")
                        if (contentRange != null) {
                            append(HttpHeaders.ContentRange, contentRange)
                        }
                    }
                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        response.bodyAsChannel().copyTo(channel)
                    }
                }
            )
        }
    } catch (e: ResponseException) {
        val status = e.response.status
        logger.w { "Segment fallback failed (HTTP $status) for: [$segmentUrl]" }
        respond(status, "Upstream error: $status")
    } catch (e: IOException) {
        val errorDetail = e.message ?: "Connection lost"
        logger.w { "Segment fallback network failure: $errorDetail for: [$segmentUrl]" }
        respond(HttpStatusCode.BadGateway, "Network failure: $errorDetail")
    }
}
