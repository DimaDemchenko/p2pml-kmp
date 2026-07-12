package com.novage.p2pml.internal.server.services

import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ManifestService(
    private val parser: HlsManifestManager,
    private val engineManager: P2PEngine,
    private val onManifestChanged: suspend () -> Unit
) {
    private val logger = CoreLogger("ManifestService")

    private var isInitialManifestProcessed = false
    private val mutex = Mutex()

    /**
     * [requestUrl] is the URL the player asked the local proxy for — the identity every stream is
     * tracked and synced under. [responseUrl] is where the upstream actually served the content
     * from (after redirects) and is only used to resolve relative URLs. CDNs commonly 302 variant
     * requests (signed-URL refresh, multi-CDN steering); treating the redirect target as the
     * identity would reset all P2P state on every such fetch.
     */
    suspend fun processManifest(requestUrl: String, responseUrl: String, manifest: String): String = mutex.withLock {
        if (!parser.isManifestTracked(requestUrl)) {
            logger.i { "Untracked manifest detected. Resetting ManifestService state for: $requestUrl" }
            isInitialManifestProcessed = false
            onManifestChanged()
        }

        val modifiedManifest = parser.getModifiedManifest(manifest, requestUrl, responseUrl)

        val needsInitialSetup = !isInitialManifestProcessed
        if (needsInitialSetup) {
            isInitialManifestProcessed = true
        }

        syncWithEngine(requestUrl, needsInitialSetup)

        modifiedManifest
    }

    private suspend fun syncWithEngine(manifestUrl: String, needsInitialSetup: Boolean) {
        val updateStreamParams = parser.getUpdateStreamParams(manifestUrl)

        if (needsInitialSetup) {
            logger.d { "Performing initial P2P Engine setup for master manifest." }
            val streams = parser.getStreams()

            engineManager.setManifestUrl(manifestUrl)
            engineManager.sendAllStreams(streams)
            updateStreamParams?.let { engineManager.sendStream(it) }
        } else {
            if (updateStreamParams == null) {
                logger.w { "No stream parameters found for URL: $manifestUrl. Skipping engine sync." }
                return
            }
            engineManager.sendStream(updateStreamParams)
        }
    }

    suspend fun resetState() {
        mutex.withLock {
            logger.d { "Internal state reset." }
            isInitialManifestProcessed = false
        }
    }
}
