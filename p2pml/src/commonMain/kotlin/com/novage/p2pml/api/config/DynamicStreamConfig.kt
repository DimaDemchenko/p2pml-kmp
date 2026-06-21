package com.novage.p2pml.api.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Subset of [StreamConfig] properties that can be updated at runtime
 * via [P2PMediaLoader.applyDynamicConfig] without restarting the engine.
 *
 * @see DynamicCoreConfig
 */
@Serializable
class DynamicStreamConfig {
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
}
