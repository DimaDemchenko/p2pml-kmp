package com.novage.p2pml.server

import com.novage.p2pml.httpClient.createHttpClient
import com.novage.p2pml.resources.INDEX_HTML
import com.novage.p2pml.resources.P2PML_CORE_JS
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.decodeBase64String
import kotlinx.coroutines.runBlocking

class ServerModule(private val onServerStarted: () -> Unit) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? =
        null
    private var client = createHttpClient()

    fun start(port: Int = 8080) {
        if (server != null) return

        try {
            server =
                embeddedServer(CIO, port) {
                        setupRouting(this)
                        subscribeToServerStarted(this)
                    }
                    .start(wait = false)
        } catch (e: Exception) {
            val message = e.message ?: "Failed to start server on port $port"
        }
    }

    private fun setupRouting(application: Application) {
        application.routing {
            get("/manifest/{manifestUrl}") {
                val manifestUrl =
                    call.parameters["manifestUrl"]
                        ?: return@get call.respondText(
                            "Missing manifest URL",
                            status = HttpStatusCode.BadRequest,
                        )
                val decodedManifestUrl = manifestUrl.decodeBase64String().decodeURLQueryComponent()
                val manifest = client.get(decodedManifestUrl).bodyAsText()
                println("Manifest request: $manifest")

                call.respondText(manifest, ContentType.parse("application/vnd.apple.mpegurl"))
            }

            get("/static/") {
                println("Static request")
                call.respondText(INDEX_HTML, ContentType.Text.Html)
            }
            get("/static/js") {
                println("Static JS request")
                call.respondText(P2PML_CORE_JS, ContentType.Application.JavaScript)
            }
        }
    }

    fun fetchManifest(manifest: String): String = runBlocking {
        return@runBlocking client.get(manifest).bodyAsText()
    }

    private fun subscribeToServerStarted(application: Application) {
        application.monitor.subscribe(ApplicationStarted) { onServerStarted() }
    }
}
