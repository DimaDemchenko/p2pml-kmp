package com.novage.p2pml.internal.session

import com.novage.p2pml.api.config.DynamicCoreConfig
import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.server.config.LocalUrlFactory

internal class P2PSession(
    private val engineManager: P2PEngine,
    private val urlFactory: LocalUrlFactory,
    private val teardownAction: suspend () -> Unit
) {
    fun createPlaybackUrl(manifestUrl: String): String = urlFactory.buildManifestUrl(manifestUrl)

    fun subscribeToEvent(eventName: String) = engineManager.subscribeToP2PEvent(eventName)

    fun unsubscribeFromEvent(eventName: String) = engineManager.unsubscribeFromP2PEvent(eventName)

    fun applyDynamicConfig(config: DynamicCoreConfig) {
        engineManager.applyDynamicConfig(config)
    }

    suspend fun destroy() {
        teardownAction()
    }
}
