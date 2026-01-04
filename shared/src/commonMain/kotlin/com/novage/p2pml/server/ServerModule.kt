package com.novage.p2pml.server

import com.novage.p2pml.http.createHttpClient
import com.novage.p2pml.parser.HlsManifestParser
import com.novage.p2pml.domain.interfaces.PlaybackProvider
import com.novage.p2pml.domain.interfaces.P2PEngine
import com.novage.p2pml.server.config.LocalUrlFactory
import com.novage.p2pml.server.plugins.configureCORS
import com.novage.p2pml.server.routes.configureRoutes
import com.novage.p2pml.server.services.ManifestService
import com.novage.p2pml.server.services.SegmentService
import com.novage.p2pml.utils.CoreLogger // <--- Import
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking

internal class ServerModule(
    playbackProvider: PlaybackProvider,
    engineManager: P2PEngine,
    urlFactory: LocalUrlFactory,
    private val onServerStarted: (serverPort: Int) -> Unit,
) {
    private val logger = CoreLogger("ServerModule")

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var client = createHttpClient()
    private var hlsManifestParser = HlsManifestParser(playbackProvider, urlFactory)

    private var manifestService = ManifestService(hlsManifestParser, engineManager) {
        logger.d { "Resetting playback and parser state" }
        playbackProvider.resetData()
        hlsManifestParser.reset()
    }

    private var segmentService = SegmentService(engineManager)

    fun start() {
        if (server != null) {
            logger.w { "Attempted to start server, but it is already running. Ignoring." }
            return
        }

        try {
            logger.d { "Starting local P2P Server..." }

            val serverInstance = embeddedServer(CIO, port = 0, host = "0.0.0.0") {
                configureCORS()
                configureRoutes(client, manifestService, hlsManifestParser, segmentService)
            }.start(wait = false)

            server = serverInstance

            runBlocking {
                val assignedPort = serverInstance.engine.resolvedConnectors()
                    .firstOrNull()?.port ?: throw Exception("Failed to retrieve assigned port")

                logger.i { "Server successfully bound to port: $assignedPort" }
                onServerStarted(assignedPort)
            }

        } catch (e: Exception) {
            logger.e(e) { "CRITICAL: P2P Server failed to start! ${e.message}" }
            throw e
        }
    }

    fun stop() {
        logger.i { "Stopping P2P Server..." }

        runBlocking {
            segmentService.reset()
            manifestService.resetState()
        }

        server?.stop(gracePeriodMillis = 100, timeoutMillis = 500)
        server = null

        try {
            client.close()
            logger.d { "HTTP client closed." }
        } catch (e: Exception) {
            logger.w { "Error closing HTTP client: ${e.message}" }
        }
    }
}