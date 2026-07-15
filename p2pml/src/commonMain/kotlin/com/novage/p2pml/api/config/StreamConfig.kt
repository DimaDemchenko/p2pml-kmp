package com.novage.p2pml.api.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Per-stream P2P configuration. Override specific properties to customize behavior for
 * a single stream type; numeric properties left at [USE_ENGINE_DEFAULT] and booleans
 * that were never assigned will use the JS engine's default (or the top-level
 * [CoreConfig] value). A boolean, once assigned, is always serialized — including
 * `false`, so a per-stream `false` can override a top-level `true`.
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
    var swarmId: String? = null

    @Transient var validateP2PSegmentJs: String? = null

    @Transient var validateHTTPSegmentJs: String? = null

    @Transient var httpRequestSetupJs: String? = null
}
