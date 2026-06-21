package com.novage.p2pml.api.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Sentinel value indicating "use the JS engine's built-in default."
 *
 * Numeric config properties default to this value. When serialized to JSON,
 * the JS engine recognizes it and applies its own default instead.
 */
const val USE_ENGINE_DEFAULT = -1

/**
 * Core P2P engine configuration passed to [P2PMediaLoader] at initialization.
 * Set only the properties you want to override; properties that remain at their Kotlin
 * defaults ([USE_ENGINE_DEFAULT] for numeric fields, `false` for booleans) are omitted
 * during JSON serialization (`encodeDefaults = false`) and the JS engine fills in its
 * own defaults.
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

    var isP2PUploadDisabled: Boolean = false
    var isP2PDisabled: Boolean = false
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
