package com.novage.p2pml.server.routes

import com.novage.p2pml.parser.HlsManifestParser
import com.novage.p2pml.server.services.ManifestService
import com.novage.p2pml.server.services.SegmentService
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
data class ManifestFetchResult(val manifestContent: String, val responseUrl: String)

internal fun Application.configureRoutes(
    client: HttpClient,
    manifestService: ManifestService,
    manifestParser: HlsManifestParser,
    segmentService: SegmentService,
) {
    routing {
        registerManifestRoute(client, manifestService)
        registerSegmentRoutes(client, segmentService, manifestParser)
        registerWebAssets()
    }
}


