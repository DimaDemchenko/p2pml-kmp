package com.novage.p2pml.server

import com.novage.p2pml.httpClient.createHttpClient
import com.novage.p2pml.parser.HlsManifestParser
import com.novage.p2pml.providers.PlaybackProvider
import com.novage.p2pml.engine.P2PEngine
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import kotlinx.coroutines.runBlocking

internal class ServerModule(
    playbackProvider: PlaybackProvider,
    engineManager: P2PEngine,
    urlFactory: LocalUrlFactory,
    private val onServerStarted: (serverPort: Int) -> Unit,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var client = createHttpClient()
    private var hlsManifestParser = HlsManifestParser(playbackProvider, urlFactory)
    private var manifestHandler = ManifestHandler(hlsManifestParser, engineManager) {
        playbackProvider.resetData()
        hlsManifestParser.reset()
    }
    private var segmentHandler = SegmentHandler(engineManager)

    fun start() {
        if (server != null) return

        try {
            val serverInstance = embeddedServer(CIO, port = 0, host = "0.0.0.0") {
                configureCORS(this)
                configureRoutes(client, manifestHandler, hlsManifestParser, segmentHandler)
            }.start(wait = false)

            server = serverInstance

            runBlocking {
                val assignedPort = serverInstance.engine.resolvedConnectors()
                    .firstOrNull()?.port ?: throw Exception("Failed to retrieve assigned port")

                println("🚀 Server successfully bound to port: $assignedPort")
                onServerStarted(assignedPort)
            }

        } catch (e: Exception) {
            println("❌ CRITICAL: P2P Server failed to start! ${e.message}")
            throw e
        }
    }

    private fun configureCORS(application: Application) {
        application.install(CORS) {
            anyHost()

            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.AccessControlAllowOrigin)
        }
    }

    fun stop() {
        runBlocking {
            segmentHandler.reset()
            manifestHandler.reset()
        }

        server?.stop(gracePeriodMillis = 100, timeoutMillis = 500)
        server = null

        try {
            client.close()
        } catch (e: Exception) {
            println("Error closing HTTP client: ${e.message}")
        }
    }
}