package com.novage.p2pml.internal.server.services

import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

internal class ManifestService(
    private val parser: HlsManifestManager,
    private val engineManager: P2PEngine,
    private val onManifestChanged: suspend () -> Unit
) {
    private val logger = CoreLogger("ManifestService")

    private var isInitialManifestProcessed = false
    private val mutex = Mutex()

    suspend fun processManifest(manifestUrl: String, manifest: String): String {
        if (!parser.isManifestTracked(manifestUrl)) {
            logger.i { "Untracked manifest detected (New Session). Resetting ManifestService state for: $manifestUrl" }

            resetState()
            onManifestChanged()
        }

        val modifiedManifest = parser.getModifiedManifest(manifest, manifestUrl)
        val needsInitialSetup = checkAndSetInitialProcessing()
        syncWithEngine(manifestUrl, needsInitialSetup)

        return modifiedManifest
    }

    private suspend fun checkAndSetInitialProcessing(): Boolean = mutex.withLock {
        if (isInitialManifestProcessed) return@withLock false
        isInitialManifestProcessed = true
        return@withLock true
    }

    private suspend fun syncWithEngine(manifestUrl: String, needsInitialSetup: Boolean) {
        try {
            val updateStreamJson = parser.getUpdateStreamParamsJson(manifestUrl)
            if (needsInitialSetup) {
                logger.d { "Performing initial P2P Engine setup for master manifest." }

                val streamsJson = parser.getStreamsJson()

                engineManager.setManifestUrl(manifestUrl)
                engineManager.sendAllStreams(streamsJson)

                updateStreamJson?.let { engineManager.sendStream(it) }
            } else {
                updateStreamJson?.let { json ->
                    engineManager.sendStream(json)
                } ?: error("No stream parameters found for URL: $manifestUrl")
            }
        } catch (e: IllegalStateException) {
            logger.e(e) { "Failed to sync with Engine: State inconsistency" }
        } catch (e: SerializationException) {
            logger.e(e) { "Failed to sync with Engine: JSON serialization error" }
        }
    }

    suspend fun resetState() {
        mutex.withLock {
            logger.d { "Internal state reset." }
            isInitialManifestProcessed = false
        }
    }
}
