package com.novage.p2pml.server

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.RoutingCall

internal val excludedHeaders =
    setOf(
        HttpHeaders.Host,
        HttpHeaders.Connection,
        HttpHeaders.TransferEncoding,
        HttpHeaders.Expect,
        HttpHeaders.Upgrade,
        "Proxy-Connection",
        "Keep-Alive",
        HttpHeaders.AcceptEncoding,
    )

suspend fun fetchManifest(
    httpClient: HttpClient,
    call: RoutingCall,
    manifestUrl: String,
): ManifestFetchResult {
    val response =
        httpClient.get(manifestUrl) {
            call.request.headers.forEach { key, values ->
                if (excludedHeaders.none { it.equals(key, ignoreCase = true) }) {
                    values.forEach { value -> headers.append(key, value) }
                }
            }
        }

    val manifest = response.bodyAsText()
    val responseUrl = response.request.url.toString()

    return ManifestFetchResult(manifest, responseUrl)
}

suspend fun fetchSegment(client: HttpClient, call: RoutingCall, segmentUrl: String): ByteArray {
    val isByteRangeRequest =
        call.request.headers.contains(HttpHeaders.Range) &&
            call.request.headers[HttpHeaders.Range] != null

    val filteredUrl = if (isByteRangeRequest) segmentUrl.substringBeforeLast("|") else segmentUrl

    val response =
        client.get(filteredUrl) {
            call.request.headers.forEach { key, values ->
                if (excludedHeaders.none { it.equals(key, ignoreCase = true) }) {
                    values.forEach { value -> headers.append(key, value) }
                }
            }
        }
    return response.bodyAsBytes()
}

suspend fun respondWithSegmentBytes(
    call: ApplicationCall,
    segmentBytes: ByteArray,
    byteRange: String?,
) {
    if (byteRange != null) {
        call.respond(
            object : OutgoingContent.ByteArrayContent() {
                override val contentType: ContentType = ContentType.parse("video/mp2t")
                override val contentLength: Long = segmentBytes.size.toLong()
                override val status: HttpStatusCode = HttpStatusCode.PartialContent

                override fun bytes(): ByteArray = segmentBytes
            },
            typeInfo = null,
        )
    } else {
        call.respondBytes(segmentBytes, ContentType.Application.OctetStream)
    }
}
