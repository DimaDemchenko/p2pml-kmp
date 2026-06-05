package com.novage.p2pml.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Sentinel value indicating "use the JS engine's built-in default."
 *
 * Numeric config properties default to this value. When serialized to JSON,
 * the JS engine recognizes it and applies its own default instead.
 */
const val USE_ENGINE_DEFAULT = -1

@Serializable
data class IceServer(val urls: List<String>, val username: String? = null, val credential: String? = null)

@Serializable
data class RtcConfig(val iceServers: List<IceServer>? = null)

/**
 * Per-stream P2P configuration. Override specific properties to customize behavior for
 * a single stream type; any property left at its default value ([USE_ENGINE_DEFAULT] for
 * numeric fields, `false` for booleans) will use the JS engine's default.
 *
 * Properties are declared as class-body `var` fields (rather than constructor parameters)
 * so that Kotlin/Native exports a true no-arg `init()` to Swift/ObjC.
 * SKIE's [DefaultArgumentInterop] is disabled because the number of parameters
 * exceeds the practical 2^N overload limit.
 *
 * **Kotlin:**
 * ```kotlin
 * val stream = StreamConfig().apply {
 *     highDemandTimeWindow = 20
 *     simultaneousP2PDownloads = 5
 * }
 * ```
 *
 * **Swift:**
 * ```swift
 * let stream = StreamConfig()
 * stream.highDemandTimeWindow = 20
 * stream.simultaneousP2PDownloads = 5
 * ```
 */
@Serializable
class StreamConfig {
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
}

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
