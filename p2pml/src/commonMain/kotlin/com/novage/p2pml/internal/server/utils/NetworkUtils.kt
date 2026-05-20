package com.novage.p2pml.internal.server.utils

import com.novage.p2pml.P2PMediaLoaderErrorType
import com.novage.p2pml.internal.server.services.SegmentPayload
import com.novage.p2pml.internal.utils.RuntimeErrorDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
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
import io.ktor.server.response.respondBytes
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.IOException

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

private fun buildContentRange(rangeHeader: String, contentLength: Long?): String? {
    if (!rangeHeader.startsWith(BYTES_PREFIX) || rangeHeader.contains(",")) {
        return null
    }

    val parts = rangeHeader.substring(BYTES_PREFIX.length).trim().split('-')
    if (parts.size != 2) {
        return null
    }

    val startStr = parts[0].trim()
    val endStr = parts[1].trim()

    val start = startStr.toLongOrNull()
    if (start == null || start < 0) {
        return null
    }

    val end = if (endStr.isNotEmpty()) {
        endStr.toLongOrNull()
    } else if (contentLength != null && contentLength > 0) {
        start + contentLength - 1
    } else {
        null
    }

    return if (end != null && end >= start) "bytes $start-$end/*" else null
}

internal suspend fun ApplicationCall.respondVideoSegmentStream(payload: SegmentPayload) {
    val rangeHeader = request.headers[HttpHeaders.Range]
    val contentType = ContentType.Application.OctetStream

    val contentRange = rangeHeader?.let { buildContentRange(it, payload.contentLength) }

    respond(
        object : OutgoingContent.ReadChannelContent() {
            override val contentType = contentType
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

internal suspend fun ApplicationCall.respondFallback(
    httpClient: HttpClient,
    segmentUrl: String,
    errorDispatcher: RuntimeErrorDispatcher
) {
    val cleanUrl = segmentUrl.substringBeforeLast("|")
    try {
        httpClient.prepareGet(cleanUrl) {
            copyProxyHeaders(request.headers)
        }.execute { response ->
            val contentType = response.contentType() ?: ContentType.Application.OctetStream
            val contentLength = response.contentLength()
            val contentRange = response.headers[HttpHeaders.ContentRange]
            val channel = response.bodyAsChannel()

            respond(
                object : OutgoingContent.ReadChannelContent() {
                    override val contentType = contentType
                    override val contentLength = contentLength
                    override val status = response.status
                    override val headers = Headers.build {
                        append(HttpHeaders.AcceptRanges, "bytes")
                        if (contentRange != null) {
                            append(HttpHeaders.ContentRange, contentRange)
                        }
                    }
                    override fun readFrom(): ByteReadChannel = channel
                }
            )
        }
    } catch (e: ResponseException) {
        val status = e.response.status

        errorDispatcher.tryEmit(
            P2PMediaLoaderErrorType.SEGMENT_DOWNLOAD_ERROR,
            "Fallback failed (HTTP $status) for: [$segmentUrl]"
        )
        respond(HttpStatusCode.BadGateway, "Upstream error: $status")
    } catch (e: IOException) {
        val errorDetail = e.message ?: "Connection lost"

        errorDispatcher.tryEmit(
            P2PMediaLoaderErrorType.SEGMENT_DOWNLOAD_ERROR,
            "Fallback network failure: $errorDetail for: [$segmentUrl]"
        )
        respond(HttpStatusCode.BadGateway, "Network failure: $errorDetail")
    }
}
