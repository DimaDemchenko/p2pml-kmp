package com.novage.p2pml.server

import com.novage.p2pml.parser.HlsManifestParser
import com.novage.p2pml.webview.WebViewManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ManifestHandler(
    private val parser: HlsManifestParser,
    private val webViewManager: WebViewManager,
    private val onManifestChanged: suspend () -> Unit,
) {
    private var isInitialManifestProcessed = false
    private val mutex = Mutex()

    suspend fun getModifiedManifest(manifestUrl: String, manifest: String): String {
        val doesManifestExist = parser.doesManifestExist(manifestUrl)

        if (!doesManifestExist) {
            reset()
            onManifestChanged()
        }

        val modifiedManifest = parser.getModifiedManifest(manifest, manifestUrl)
        val needsInitialSetup = checkAndSetInitialProcessing()
        handleUpdate(manifestUrl, needsInitialSetup)

        return modifiedManifest
    }

    private suspend fun checkAndSetInitialProcessing(): Boolean =
        mutex.withLock {
            if (isInitialManifestProcessed) return false

            isInitialManifestProcessed = true
            return true
        }

    private suspend fun handleUpdate(manifestUrl: String, needsInitialSetup: Boolean) {
        try {
            val updateStreamJson = parser.getUpdateStreamParamsJson(manifestUrl)

            if (needsInitialSetup) {
                val streamsJson = parser.getStreamsJson()

                // webViewManager.sendInitialMessage()
                webViewManager.setManifestUrl(manifestUrl)
                webViewManager.sendAllStreams(streamsJson)

                updateStreamJson?.let { webViewManager.sendStream(it) }
            } else {
                updateStreamJson?.let { json -> webViewManager.sendStream(json) }
                    ?: throw Exception("updateStreamJson is null")
            }
        } catch (e: Exception) {
            // Log.e(TAG, "Unexpected error occurred: ${e.message}")
        }
    }

    private suspend fun reset() {
        mutex.withLock { isInitialManifestProcessed = false }
    }
}
