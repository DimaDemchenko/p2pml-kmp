package com.novage.p2pml.server.services

import com.novage.p2pml.domain.interfaces.P2PEngine
import com.novage.p2pml.parser.HlsManifestParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ManifestService(
    private val parser: HlsManifestParser,
    private val engineManager: P2PEngine,
    private val onManifestChanged: suspend () -> Unit,
) {
    private var isInitialManifestProcessed = false
    private val mutex = Mutex()

    suspend fun processManifest(manifestUrl: String, manifest: String): String {
        if (!parser.doesManifestExist(manifestUrl)) {
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
                val streamsJson = parser.getStreamsJson()

                engineManager.setManifestUrl(manifestUrl)
                engineManager.sendAllStreams(streamsJson)

                updateStreamJson?.let { engineManager.sendStream(it) }
            } else {
                updateStreamJson?.let { json -> engineManager.sendStream(json) }
                    ?: throw Exception("updateStreamJson is null")
            }
        } catch (e: Exception) {
            println("❌ Error syncing manifest with Engine: ${e.message}")
        }
    }

    suspend fun resetState() {
        mutex.withLock { isInitialManifestProcessed = false }
    }
}