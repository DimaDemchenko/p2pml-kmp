package com.novage.p2pml.internal.server.routes

import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.server.services.ManifestService
import com.novage.p2pml.internal.server.services.SegmentService
import com.novage.p2pml.internal.utils.RuntimeErrorDispatcher
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

internal fun Application.configureRoutes(
    httpClient: HttpClient,
    manifestService: ManifestService,
    hlsManifestManager: HlsManifestManager,
    segmentService: SegmentService,
    errorDispatcher: RuntimeErrorDispatcher
) {
    routing {
        registerWebAssets()
        registerManifestRoute(httpClient, manifestService, errorDispatcher)
        registerSegmentRoutes(httpClient, segmentService, hlsManifestManager, errorDispatcher)
    }
}
