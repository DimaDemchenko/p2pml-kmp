package com.novage.p2pml.server

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.server.routing.RoutingCall

suspend fun fetchManifest(
    httpClient: HttpClient,
    call: RoutingCall,
    manifestUrl: String,
): ManifestFetchResult {
    val response =
        httpClient.get(manifestUrl) {
            call.request.headers.forEach { key, values ->
                if (!key.equals("Host", ignoreCase = true)) {
                    values.forEach { value -> headers.append(key, value) }
                }
            }
        }

    val manifest = response.bodyAsText()
    val responseUrl = response.request.url.toString()

    return ManifestFetchResult(manifest, responseUrl)
}

suspend fun fetchSegment(client: HttpClient, call: RoutingCall, segmentUrl: String): ByteArray {
    val response =
        client.get(segmentUrl) {
            call.request.headers.forEach { key, values ->
                if (!key.equals("Host", ignoreCase = true)) {
                    values.forEach { value -> headers.append(key, value) }
                }
            }
        }

    return response.bodyAsBytes()
}
