package com.novage.p2pml.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class IceServer(val urls: List<String>, val username: String? = null, val credential: String? = null)

@Serializable
data class RtcConfig(val iceServers: List<IceServer>? = null)

/**
 * Per-stream P2P configuration. Override specific properties to customize behavior for
 * a single stream type; any property left at its default value (`-1` for numeric fields,
 * `false` for booleans) will use the JS engine's default.
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
    var highDemandTimeWindow: Int = -1
    var httpDownloadTimeWindow: Int = -1
    var httpDownloadInitialTimeoutMs: Int = -1
    var p2pDownloadTimeWindow: Int = -1
    var simultaneousHttpDownloads: Int = -1
    var simultaneousP2PDownloads: Int = -1
    var webRtcMaxMessageSize: Int = -1
    var p2pNotReceivingBytesTimeoutMs: Int = -1
    var p2pInactiveLoaderDestroyTimeoutMs: Int = -1
    var httpNotReceivingBytesTimeoutMs: Int = -1
    var httpErrorRetries: Int = -1
    var p2pErrorRetries: Int = -1
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
 * defaults (`-1` for numeric fields, `false` for booleans) are omitted during JSON
 * serialization (`encodeDefaults = false`) and the JS engine fills in its own defaults.
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
    var segmentMemoryStorageLimit: Int = -1

    @Transient var customSegmentStorageFactoryJs: String? = null

    var isP2PUploadDisabled: Boolean = false
    var isP2PDisabled: Boolean = false
    var highDemandTimeWindow: Int = -1
    var httpDownloadTimeWindow: Int = -1
    var httpDownloadInitialTimeoutMs: Int = -1
    var p2pDownloadTimeWindow: Int = -1
    var simultaneousHttpDownloads: Int = -1
    var simultaneousP2PDownloads: Int = -1
    var webRtcMaxMessageSize: Int = -1
    var p2pNotReceivingBytesTimeoutMs: Int = -1
    var p2pInactiveLoaderDestroyTimeoutMs: Int = -1
    var httpNotReceivingBytesTimeoutMs: Int = -1
    var httpErrorRetries: Int = -1
    var p2pErrorRetries: Int = -1
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
    @SerialName("highDemandTimeWindow")
    private var _highDemandTimeWindow: Int? = null

    @SerialName("httpDownloadTimeWindow")
    private var _httpDownloadTimeWindow: Int? = null

    @SerialName("httpDownloadInitialTimeoutMs")
    private var _httpDownloadInitialTimeoutMs: Int? = null

    @SerialName("p2pDownloadTimeWindow")
    private var _p2pDownloadTimeWindow: Int? = null

    @SerialName("simultaneousHttpDownloads")
    private var _simultaneousHttpDownloads: Int? = null

    @SerialName("simultaneousP2PDownloads")
    private var _simultaneousP2PDownloads: Int? = null

    @SerialName("webRtcMaxMessageSize")
    private var _webRtcMaxMessageSize: Int? = null

    @SerialName("p2pNotReceivingBytesTimeoutMs")
    private var _p2pNotReceivingBytesTimeoutMs: Int? = null

    @SerialName("p2pInactiveLoaderDestroyTimeoutMs")
    private var _p2pInactiveLoaderDestroyTimeoutMs: Int? = null

    @SerialName("httpNotReceivingBytesTimeoutMs")
    private var _httpNotReceivingBytesTimeoutMs: Int? = null

    @SerialName("httpErrorRetries")
    private var _httpErrorRetries: Int? = null

    @SerialName("p2pErrorRetries")
    private var _p2pErrorRetries: Int? = null

    var highDemandTimeWindow: Int
        get() = _highDemandTimeWindow ?: -1
        set(value) {
            _highDemandTimeWindow = value
        }

    var httpDownloadTimeWindow: Int
        get() = _httpDownloadTimeWindow ?: -1
        set(value) {
            _httpDownloadTimeWindow = value
        }

    var httpDownloadInitialTimeoutMs: Int
        get() = _httpDownloadInitialTimeoutMs ?: -1
        set(value) {
            _httpDownloadInitialTimeoutMs = value
        }

    var p2pDownloadTimeWindow: Int
        get() = _p2pDownloadTimeWindow ?: -1
        set(value) {
            _p2pDownloadTimeWindow = value
        }

    var simultaneousHttpDownloads: Int
        get() = _simultaneousHttpDownloads ?: -1
        set(value) {
            _simultaneousHttpDownloads = value
        }

    var simultaneousP2PDownloads: Int
        get() = _simultaneousP2PDownloads ?: -1
        set(value) {
            _simultaneousP2PDownloads = value
        }

    var webRtcMaxMessageSize: Int
        get() = _webRtcMaxMessageSize ?: -1
        set(value) {
            _webRtcMaxMessageSize = value
        }

    var p2pNotReceivingBytesTimeoutMs: Int
        get() = _p2pNotReceivingBytesTimeoutMs ?: -1
        set(value) {
            _p2pNotReceivingBytesTimeoutMs = value
        }

    var p2pInactiveLoaderDestroyTimeoutMs: Int
        get() = _p2pInactiveLoaderDestroyTimeoutMs ?: -1
        set(value) {
            _p2pInactiveLoaderDestroyTimeoutMs = value
        }

    var httpNotReceivingBytesTimeoutMs: Int
        get() = _httpNotReceivingBytesTimeoutMs ?: -1
        set(value) {
            _httpNotReceivingBytesTimeoutMs = value
        }

    var httpErrorRetries: Int
        get() = _httpErrorRetries ?: -1
        set(value) {
            _httpErrorRetries = value
        }

    var p2pErrorRetries: Int
        get() = _p2pErrorRetries ?: -1
        set(value) {
            _p2pErrorRetries = value
        }

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
    @SerialName("segmentMemoryStorageLimit")
    private var _segmentMemoryStorageLimit: Int? = null

    @Transient var customSegmentStorageFactoryJs: String? = null

    @SerialName("highDemandTimeWindow")
    private var _highDemandTimeWindow: Int? = null

    @SerialName("httpDownloadTimeWindow")
    private var _httpDownloadTimeWindow: Int? = null

    @SerialName("httpDownloadInitialTimeoutMs")
    private var _httpDownloadInitialTimeoutMs: Int? = null

    @SerialName("p2pDownloadTimeWindow")
    private var _p2pDownloadTimeWindow: Int? = null

    @SerialName("simultaneousHttpDownloads")
    private var _simultaneousHttpDownloads: Int? = null

    @SerialName("simultaneousP2PDownloads")
    private var _simultaneousP2PDownloads: Int? = null

    @SerialName("webRtcMaxMessageSize")
    private var _webRtcMaxMessageSize: Int? = null

    @SerialName("p2pNotReceivingBytesTimeoutMs")
    private var _p2pNotReceivingBytesTimeoutMs: Int? = null

    @SerialName("p2pInactiveLoaderDestroyTimeoutMs")
    private var _p2pInactiveLoaderDestroyTimeoutMs: Int? = null

    @SerialName("httpNotReceivingBytesTimeoutMs")
    private var _httpNotReceivingBytesTimeoutMs: Int? = null

    @SerialName("httpErrorRetries")
    private var _httpErrorRetries: Int? = null

    @SerialName("p2pErrorRetries")
    private var _p2pErrorRetries: Int? = null

    var segmentMemoryStorageLimit: Int
        get() = _segmentMemoryStorageLimit ?: -1
        set(value) {
            _segmentMemoryStorageLimit = value
        }

    var highDemandTimeWindow: Int
        get() = _highDemandTimeWindow ?: -1
        set(value) {
            _highDemandTimeWindow = value
        }

    var httpDownloadTimeWindow: Int
        get() = _httpDownloadTimeWindow ?: -1
        set(value) {
            _httpDownloadTimeWindow = value
        }

    var httpDownloadInitialTimeoutMs: Int
        get() = _httpDownloadInitialTimeoutMs ?: -1
        set(value) {
            _httpDownloadInitialTimeoutMs = value
        }

    var p2pDownloadTimeWindow: Int
        get() = _p2pDownloadTimeWindow ?: -1
        set(value) {
            _p2pDownloadTimeWindow = value
        }

    var simultaneousHttpDownloads: Int
        get() = _simultaneousHttpDownloads ?: -1
        set(value) {
            _simultaneousHttpDownloads = value
        }

    var simultaneousP2PDownloads: Int
        get() = _simultaneousP2PDownloads ?: -1
        set(value) {
            _simultaneousP2PDownloads = value
        }

    var webRtcMaxMessageSize: Int
        get() = _webRtcMaxMessageSize ?: -1
        set(value) {
            _webRtcMaxMessageSize = value
        }

    var p2pNotReceivingBytesTimeoutMs: Int
        get() = _p2pNotReceivingBytesTimeoutMs ?: -1
        set(value) {
            _p2pNotReceivingBytesTimeoutMs = value
        }

    var p2pInactiveLoaderDestroyTimeoutMs: Int
        get() = _p2pInactiveLoaderDestroyTimeoutMs ?: -1
        set(value) {
            _p2pInactiveLoaderDestroyTimeoutMs = value
        }

    var httpNotReceivingBytesTimeoutMs: Int
        get() = _httpNotReceivingBytesTimeoutMs ?: -1
        set(value) {
            _httpNotReceivingBytesTimeoutMs = value
        }

    var httpErrorRetries: Int
        get() = _httpErrorRetries ?: -1
        set(value) {
            _httpErrorRetries = value
        }

    var p2pErrorRetries: Int
        get() = _p2pErrorRetries ?: -1
        set(value) {
            _p2pErrorRetries = value
        }

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
