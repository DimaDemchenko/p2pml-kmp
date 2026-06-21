package com.novage.p2pml.api.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
