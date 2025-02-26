package com.novage.p2pml.server

import com.novage.p2pml.parser.HlsManifestParser
import com.novage.p2pml.parser.decodeBase64Url
import com.novage.p2pml.resources.INDEX_HTML
import com.novage.p2pml.resources.P2PML_CORE_JS
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.Application
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

data class ManifestFetchResult(val manifestContent: String, val responseUrl: String)

internal fun Application.configureRoutes(client: HttpClient, manifestParser: HlsManifestParser) {
    routing {
        registerManifestRoute(client, manifestParser)
        registerSegmentRoute(client)
        registerStaticRoute()
        registerStaticJsRoute()
    }
}

internal fun Route.registerManifestRoute(
    httpClient: HttpClient,
    manifestParser: HlsManifestParser,
) {
    get("/manifest/{manifestUrl}") {
        println("Received manifest request")
        val manifestUrl = call.parameters["manifestUrl"]

        if (manifestUrl.isNullOrBlank()) {
            call.respondText("Missing manifest URL", status = HttpStatusCode.BadRequest)
            return@get
        }
        try {
            val fetchResult = fetchManifest(httpClient, call, manifestUrl)
            val doesManifestExist = manifestParser.doesManifestExist(manifestUrl)

            if (!doesManifestExist) {
                // reset()
                // onManifestChanged()
            }

            val modifiedManifest =
                manifestParser.getModifiedManifest(
                    fetchResult.manifestContent,
                    fetchResult.responseUrl,
                )
            println("Modified manifest: $modifiedManifest")
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

fun Route.registerSegmentRoute(httpClient: HttpClient) {
    get("/segment/{segmentUrl}") {
        val encodedSegmentUrl = call.parameters["segmentUrl"]

        if (encodedSegmentUrl.isNullOrBlank()) {
            call.respondText("Missing segment URL", status = HttpStatusCode.BadRequest)
            return@get
        }

        val segmentUrl = decodeBase64Url(encodedSegmentUrl)
        val byteRange = call.request.headers[HttpHeaders.Range]

        try {
            val segmentBytes = fetchSegment(httpClient, call, segmentUrl)

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
        } catch (ex: Exception) {
            call.respondText(
                "Error processing segment",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }
}

fun Route.registerStaticRoute() {
    get("/static/") { call.respondText(INDEX_HTML, ContentType.Text.Html) }
}

fun Route.registerStaticJsRoute() {
    get("/static/js") { call.respondText(P2PML_CORE_JS, ContentType.Application.JavaScript) }
}
