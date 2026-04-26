package com.novage.p2pml.internal.session

import com.novage.p2pml.api.models.DynamicCoreConfig
import com.novage.p2pml.api.models.toJsExpression
import com.novage.p2pml.internal.engine.P2PEngineManager
import com.novage.p2pml.internal.server.config.LocalUrlFactory

internal class P2PSession(
    val engineManager: P2PEngineManager,
    private val urlFactory: LocalUrlFactory,
    private val teardownAction: suspend () -> Unit
) {
    fun getManifestUrl(manifestUrl: String): String = urlFactory.buildManifestUrl(manifestUrl)

    fun applyDynamicConfig(config: DynamicCoreConfig) {
        engineManager.applyDynamicConfig(config.toJsExpression())
    }

    suspend fun destroy() {
        teardownAction()
    }
}
