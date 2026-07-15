package com.novage.p2pml.api.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Sentinel value indicating "use the JS engine's built-in default."
 *
 * Numeric config properties default to this value. Properties equal to it are omitted
 * from the serialized JSON payload entirely, and the JS engine applies its own default.
 * Any other value — including other negative numbers — is sent to the engine as-is.
 */
const val USE_ENGINE_DEFAULT = -1

/**
 * Core P2P engine configuration passed to [P2PMediaLoader] at initialization.
 * Set only the properties you want to override; numeric properties left at
 * [USE_ENGINE_DEFAULT] and booleans that were never assigned are omitted during JSON
 * serialization (`encodeDefaults = false`) and the JS engine fills in its own defaults.
 * A boolean, once assigned, is always serialized — including `false`, so a per-stream
 * `false` can override a top-level `true`.
 *
 * **Note on mutability:** Properties are `var` for cross-platform interop (Kotlin/Native
 * does not export constructor default values to Swift/ObjC). The config is serialized
 * to JSON and sent to the JS engine **once** when the loader is initialized.
 * Mutating properties after initialization has no effect. To change settings at
 * runtime, use [DynamicCoreConfig] with [P2PMediaLoader.applyDynamicConfig] instead.
 *
 * Top-level stream properties (e.g. [highDemandTimeWindow]) apply to **both** streams
 * unless overridden via [mainStream] or [secondaryStream].
 *
 * **Kotlin:**
 * ```kotlin
 * val config = CoreConfig().apply {
 *     isP2PDisabled = false
 *     highDemandTimeWindow = 20
 *     simultaneousP2PDownloads = 3
 * }
 * ```
 *
 * **Swift:**
 * ```swift
 * let config = CoreConfig()
 * config.isP2PDisabled = false
 * config.highDemandTimeWindow = 20
 * config.simultaneousP2PDownloads = 3
 * ```
 */
@Serializable
class CoreConfig {
    var segmentMemoryStorageLimit: Int = USE_ENGINE_DEFAULT

    @Transient var customSegmentStorageFactoryJs: String? = null

    @SerialName("isP2PUploadDisabled")
    private var _isP2PUploadDisabled: Boolean? = null

    @SerialName("isP2PDisabled")
    private var _isP2PDisabled: Boolean? = null

    var isP2PUploadDisabled: Boolean
        get() = _isP2PUploadDisabled ?: false
        set(value) {
            _isP2PUploadDisabled = value
        }

    var isP2PDisabled: Boolean
        get() = _isP2PDisabled ?: false
        set(value) {
            _isP2PDisabled = value
        }

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
    var p2pMaxPeers: Int = USE_ENGINE_DEFAULT
    var p2pChurnMaxPeersMultiplier: Double = USE_ENGINE_DEFAULT.toDouble()
    var p2pChurnCleanupIntervalMs: Int = USE_ENGINE_DEFAULT
    var p2pChurnGracePeriodMs: Int = USE_ENGINE_DEFAULT
    var webRtcOffersCount: Int = USE_ENGINE_DEFAULT
    var webRtcOfferTimeoutMs: Int = USE_ENGINE_DEFAULT
    var webRtcIceGatheringTimeoutMs: Int = USE_ENGINE_DEFAULT
    var webRtcConnectionTimeoutMs: Int = USE_ENGINE_DEFAULT
    var announceTrackers: List<String>? = null
    var rtcConfig: RtcConfig? = null
    var trackerClientVersionPrefix: String? = null
    var swarmId: String? = null

    @Transient var validateP2PSegmentJs: String? = null

    @Transient var validateHTTPSegmentJs: String? = null

    @Transient var httpRequestSetupJs: String? = null

    var mainStream: StreamConfig? = null
    var secondaryStream: StreamConfig? = null
}
