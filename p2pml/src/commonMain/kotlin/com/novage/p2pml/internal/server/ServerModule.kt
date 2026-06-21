package com.novage.p2pml.internal.server

import com.novage.p2pml.api.errors.P2PMediaLoaderErrorCode
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.server.plugins.configureCORS
import com.novage.p2pml.internal.server.routes.configureRoutes
import com.novage.p2pml.internal.server.services.ManifestService
import com.novage.p2pml.internal.server.services.SegmentService
import com.novage.p2pml.internal.utils.CoreLogger
import io.ktor.client.HttpClient
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

internal class ServerModule(
    private val client: HttpClient,
    private val hlsManifestManager: HlsManifestManager,
    private val manifestService: ManifestService,
    private val segmentService: SegmentService,
    private val enableCors: Boolean
) {
    private val logger = CoreLogger("ServerModule")

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    private val serverMutex = Mutex()
    private var isDestroyed = false

    suspend fun start(): Int = serverMutex.withLock {
        if (isDestroyed) {
            throw CancellationException("ServerModule was destroyed before it could start.")
        }

        if (server != null) {
            val port = server?.engine?.resolvedConnectors()?.firstOrNull()?.port
                ?: throw P2PMediaLoaderException(
                    P2PMediaLoaderErrorCode.SERVER_START_FAILED,
                    "Server running but port unknown."
                )
            logger.w { "Server is already running on port $port." }
            return port
        }

        logger.d { "Starting local P2P Server..." }

        try {
            val serverInstance = embeddedServer(CIO, host = "127.0.0.1", port = 0, watchPaths = emptyList()) {
                if (enableCors) configureCORS()
                configureRoutes(client, manifestService, hlsManifestManager, segmentService)
            }
            server = serverInstance

            serverInstance.start(wait = false)

            val assignedPort = serverInstance.engine.resolvedConnectors().firstOrNull()?.port
            checkNotNull(assignedPort) { "Server started but failed to retrieve assigned port" }

            logger.i { "Server successfully bound to port: $assignedPort" }
            return assignedPort
        } catch (e: IOException) {
            handleStartupError("Network Error starting server: ${e.message}", e)
        } catch (e: IllegalStateException) {
            handleStartupError("Invalid server state: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            handleStartupError("Invalid server configuration: ${e.message}", e)
        }
    }

    suspend fun destroy() = serverMutex.withLock {
        isDestroyed = true
        logger.i { "Destroying P2P Server module..." }
        stopServer()
    }

    private suspend fun handleStartupError(message: String, e: Exception): Nothing {
        logger.e { "Failed to start Ktor server. Forcing aggressive shutdown." }
        stopServer()

        throw P2PMediaLoaderException(
            P2PMediaLoaderErrorCode.SERVER_START_FAILED,
            message,
            cause = e
        )
    }

    private suspend fun stopServer() {
        withContext(Dispatchers.IO) {
            server?.stop(gracePeriodMillis = 0, timeoutMillis = 100)
        }
        server = null
    }
}
