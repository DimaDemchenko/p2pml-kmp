package com.novage.p2pml.server

import com.novage.p2pml.httpClient.createHttpClient
import com.novage.p2pml.parser.HlsManifestParser
import com.novage.p2pml.providers.PlaybackProvider
import com.novage.p2pml.engine.P2PEngine
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer

internal class ServerModule(
    playbackProvider: PlaybackProvider,
    engineManager: P2PEngine,
    private val onServerStarted: () -> Unit,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? =
        null
    private var client = createHttpClient()
    private var hlsManifestParser = HlsManifestParser(playbackProvider)
    private var manifestHandler =
        ManifestHandler(hlsManifestParser, engineManager) { println("Manifest changed") }
    private var segmentHandler = SegmentHandler(engineManager)

    fun start(port: Int = 8080) {
        if (server != null) return

        try {
            server =
                embeddedServer(CIO, port) {
                        configureRoutes(client, manifestHandler, hlsManifestParser, segmentHandler)
                        subscribeToServerStarted(this)
                    }
                    .start(wait = false)
        } catch (e: Exception) {
            val message = e.message ?: "Failed to start server on port $port"
            // onServerStarted Error
        }
    }

    private fun subscribeToServerStarted(application: Application) {
        application.monitor.subscribe(ApplicationStarted) { onServerStarted() }
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 100, timeoutMillis = 500)
        server = null

        try {
            client.close()
        } catch (e: Exception) {
            println("Error closing HTTP client: ${e.message}")
        }
    }
}
