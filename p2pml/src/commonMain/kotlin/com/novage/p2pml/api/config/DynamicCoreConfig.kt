package com.novage.p2pml.api.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Subset of [CoreConfig] properties that can be updated at runtime
 * via [P2PMediaLoader.applyDynamicConfig] without restarting the engine.
 *
 * Same mutability note as [CoreConfig]: properties are `var` for cross-platform interop.
 * Each call to [P2PMediaLoader.applyDynamicConfig] snapshots the current property values
 * and pushes them to the JS engine. Mutating the object afterwards has no effect
 * until you call [P2PMediaLoader.applyDynamicConfig] again.
 *
 * **Kotlin:**
 * ```kotlin
 * val config = DynamicCoreConfig().apply { isP2PDisabled = true }
 * loader.applyDynamicConfig(config)
 * ```
 *
 * **Swift:**
 * ```swift
 * let config = DynamicCoreConfig()
 * config.isP2PDisabled = true
 * try loader.applyDynamicConfig(dynamicCoreConfig: config)
 * ```
 *
 * @see CoreConfig
 */
@Serializable
class DynamicCoreConfig {
    var segmentMemoryStorageLimit: Int = USE_ENGINE_DEFAULT

    @Transient var customSegmentStorageFactoryJs: String? = null

    var highDemandTimeWindow: Int = USE_ENGINE_DEFAULT
    var httpDownloadTimeWindow: Int = USE_ENGINE_DEFAULT
    var httpDownloadInitialTimeoutMs: Int = USE_ENGINE_DEFAULT
    var p2pDownloadTimeWindow: Int = USE_ENGINE_DEFAULT
    var simultaneousHttpDownloads: Int = USE_ENGINE_DEFAULT
    var simultaneousP2PDownloads: Int = USE_ENGINE_DEFAULT
    var webRtcMaxMessageSize: Int = USE_ENGINE_DEFAULT
    var p2pNotReceivingBytesTimeoutMs: Int = USE_ENGINE_DEFAULT
    var p2pInactiveLoaderDestroyTimeoutMs: Int = USE_ENGINE_DEFAULT
    var httpNotReceivingBytesTimeoutMs: Int = USE_ENGINE_DEFAULT
    var httpErrorRetries: Int = USE_ENGINE_DEFAULT
    var p2pErrorRetries: Int = USE_ENGINE_DEFAULT

    @SerialName("isP2PDisabled")
    private var _isP2PDisabled: Boolean? = null

    @SerialName("isP2PUploadDisabled")
    private var _isP2PUploadDisabled: Boolean? = null

    var isP2PDisabled: Boolean
        get() = _isP2PDisabled ?: false
        set(value) {
            _isP2PDisabled = value
        }

    var isP2PUploadDisabled: Boolean
        get() = _isP2PUploadDisabled ?: false
        set(value) {
            _isP2PUploadDisabled = value
        }

    @Transient var validateP2PSegmentJs: String? = null

    @Transient var httpRequestSetupJs: String? = null

    var mainStream: DynamicStreamConfig? = null
    var secondaryStream: DynamicStreamConfig? = null
}
