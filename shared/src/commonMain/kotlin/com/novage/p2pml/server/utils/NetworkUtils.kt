package com.novage.p2pml.server.utils

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
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

internal suspend fun HttpClient.fetchSegment(call: ApplicationCall, segmentUrl: String): ByteArray {
    // If the URL contains a pipe '|', the part after is the byte range.
    // We strip it for the actual HTTP request, as the range header handles the rest.
    val cleanUrl = segmentUrl.substringBeforeLast("|")

    val response = this.get(cleanUrl) {
        copyProxyHeaders(call.request.headers)
    }

    return response.bodyAsBytes()
}

private fun HttpRequestBuilder.copyProxyHeaders(requestHeaders: Headers) {
    requestHeaders.forEach { key, values ->
        if (key !in EXCLUDED_PROXY_HEADERS) {
            values.forEach { value -> headers.append(key, value) }
        }
    }
}

internal suspend fun ApplicationCall.respondVideoSegment(bytes: ByteArray, byteRangeHeader: String?) {
    if (byteRangeHeader != null) {
        respond(
            object : OutgoingContent.ByteArrayContent() {
                override val contentType = ContentType.parse("video/mp2t")
                override val contentLength = bytes.size.toLong()
                override val status = HttpStatusCode.PartialContent
                override fun bytes(): ByteArray = bytes
            }
        )
    } else {
        respondBytes(bytes, ContentType.Application.OctetStream)
    }
}

internal suspend fun ApplicationCall.respondFallback(
    httpClient: HttpClient,
    segmentUrl: String,
    byteRangeHeader: String?
) {
    try {
        val bytes = httpClient.fetchSegment(this, segmentUrl)
        respondVideoSegment(bytes, byteRangeHeader)
    } catch (e: ResponseException) {
        respond(HttpStatusCode.BadGateway, "Upstream server returned error: ${e.response.status}")
    } catch (e: IOException) {
        respond(HttpStatusCode.BadGateway, "Network failure: ${e.message}")
    }
}
