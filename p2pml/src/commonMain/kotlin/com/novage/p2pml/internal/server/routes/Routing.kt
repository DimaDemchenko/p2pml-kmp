package com.novage.p2pml.internal.server.routes

import com.novage.p2pml.P2PMediaLoaderErrorType
import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.server.services.ManifestService
import com.novage.p2pml.internal.server.services.SegmentService
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

internal fun Application.configureRoutes(
    client: HttpClient,
    manifestService: ManifestService,
    manifestParser: HlsManifestManager,
    segmentService: SegmentService,
    onError: (P2PMediaLoaderErrorType, String) -> Unit
) {
    routing {
        registerManifestRoute(client, manifestService, onError)
        registerSegmentRoutes(client, segmentService, manifestParser, onError)
        registerWebAssets()
    }
}
