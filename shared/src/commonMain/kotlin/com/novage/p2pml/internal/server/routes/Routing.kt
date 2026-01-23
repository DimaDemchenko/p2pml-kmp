package com.novage.p2pml.internal.server.routes

import com.novage.p2pml.internal.parser.HlsManifestParser
import com.novage.p2pml.internal.server.services.ManifestService
import com.novage.p2pml.internal.server.services.SegmentService
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

internal fun Application.configureRoutes(
    client: HttpClient,
    manifestService: ManifestService,
    manifestParser: HlsManifestParser,
    segmentService: SegmentService
) {
    routing {
        registerManifestRoute(client, manifestService)
        registerSegmentRoutes(client, segmentService, manifestParser)
        registerWebAssets()
    }
}
