package com.novage.p2pml.server

import com.novage.p2pml.httpClient.createHttpClient
import com.novage.p2pml.parser.HlsManifestParser
import com.novage.p2pml.providers.PlaybackProvider
import io.ktor.client.request.get
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer

internal class ServerModule(
    playbackProvider: PlaybackProvider,
    private val onServerStarted: () -> Unit,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? =
        null
    private var client = createHttpClient()
    private var hlsManifestParser = HlsManifestParser(playbackProvider)

    fun start(port: Int = 8080) {
        if (server != null) return

        try {
            server =
                embeddedServer(CIO, port) {
                        configureRoutes(client, hlsManifestParser)
                        subscribeToServerStarted(this)
                    }
                    .start(wait = false)
        } catch (e: Exception) {
            val message = e.message ?: "Failed to start server on port $port"
        }
    }

    private fun subscribeToServerStarted(application: Application) {
        application.monitor.subscribe(ApplicationStarted) { onServerStarted() }
    }
}
