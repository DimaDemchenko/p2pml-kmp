package com.novage.p2pml.internal.server

import com.novage.p2pml.P2PMediaLoaderErrorType
import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.http.createHttpClient
import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.providers.SequenceStateTracker
import com.novage.p2pml.internal.server.config.LocalUrlFactory
import com.novage.p2pml.internal.server.plugins.configureCORS
import com.novage.p2pml.internal.server.routes.configureRoutes
import com.novage.p2pml.internal.server.services.ManifestService
import com.novage.p2pml.internal.server.services.SegmentService
import com.novage.p2pml.internal.utils.CoreLogger
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

internal class ServerModule(
    private val playbackProvider: PlaybackProvider,
    engineManager: P2PEngine,
    urlFactory: LocalUrlFactory,
    private val enableCors: Boolean,
    private val onServerStarted: (serverPort: Int) -> Unit,
    private val onError: (errorType: P2PMediaLoaderErrorType, message: String) -> Unit
) {
    private val logger = CoreLogger("ServerModule")

    private val client = createHttpClient()
    private val hlsManifestManager = HlsManifestManager(playbackProvider, urlFactory)
    private val manifestService = ManifestService(hlsManifestManager, engineManager) {
        logger.d { "Resetting playback and parser state" }
        playbackProvider.resetData()
        hlsManifestManager.reset()
    }
    private val sequenceStateTracker = SequenceStateTracker(playbackProvider, engineManager, hlsManifestManager)
    private val segmentService = SegmentService(engineManager, sequenceStateTracker)

    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun start() {
        if (server != null) {
            logger.w { "Server is already running. Ignoring start request." }
            return
        }

        logger.d { "Starting local P2P Server..." }

        val serverInstance = embeddedServer(CIO, port = 0, watchPaths = emptyList()) {
            if (enableCors) configureCORS()
            configureRoutes(client, manifestService, hlsManifestManager, segmentService, onError)
        }
        server = serverInstance

        sequenceStateTracker.start()

        serverScope.launch {
            try {
                serverInstance.start(wait = false)

                val assignedPort = serverInstance.engine.resolvedConnectors().firstOrNull()?.port
                    ?: error("Server started but failed to retrieve assigned port")

                logger.i { "Server successfully bound to port: $assignedPort" }

                withContext(Dispatchers.Main) {
                    onServerStarted(assignedPort)
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    onError(
                        P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR,
                        "Network Error starting server: ${e.message}"
                    )
                }
            } catch (e: IllegalStateException) {
                withContext(Dispatchers.Main) {
                    onError(
                        P2PMediaLoaderErrorType.ENGINE_STARTUP_ERROR,
                        "Invalid server state: ${e.message}"
                    )
                }
            }
        }
    }

    fun destroy() {
        logger.i { "Destroying P2P Server module..." }

        serverScope.cancel()

        runBlocking {
            segmentService.reset()
            sequenceStateTracker.reset()
            manifestService.resetState()
        }

        sequenceStateTracker.destroy()
        server?.stop()
        server = null

        client.close()
    }
}
