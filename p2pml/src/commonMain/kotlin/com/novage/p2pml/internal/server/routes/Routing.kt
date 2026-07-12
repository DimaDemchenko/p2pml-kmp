package com.novage.p2pml.internal.server.routes

import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.server.services.ManifestService
import com.novage.p2pml.internal.server.services.SegmentService
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

internal fun Application.configureRoutes(
    httpClient: HttpClient,
    manifestService: ManifestService,
    hlsManifestManager: HlsManifestManager,
    segmentService: SegmentService,
    sessionToken: String
) {
    routing {
        route("/$sessionToken") {
            registerWebAssets()
            registerManifestRoute(httpClient, manifestService)
            registerSegmentRoutes(httpClient, segmentService, hlsManifestManager)
        }
    }
}
