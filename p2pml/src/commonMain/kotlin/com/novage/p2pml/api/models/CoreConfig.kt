package com.novage.p2pml.api.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class IceServer(val urls: List<String>, val username: String? = null, val credential: String? = null)

@Serializable
data class RtcConfig(val iceServers: List<IceServer>? = null)

@Suppress("LongParameterList")
@Serializable
class StreamConfig(
    var isP2PUploadDisabled: Boolean? = null,
    var isP2PDisabled: Boolean? = null,
    var highDemandTimeWindow: Int? = null,
    var httpDownloadTimeWindow: Int? = null,
    var p2pDownloadTimeWindow: Int? = null,
    var simultaneousHttpDownloads: Int? = null,
    var simultaneousP2PDownloads: Int? = null,
    var webRtcMaxMessageSize: Int? = null,
    var p2pNotReceivingBytesTimeoutMs: Int? = null,
    var p2pInactiveLoaderDestroyTimeoutMs: Int? = null,
    var httpNotReceivingBytesTimeoutMs: Int? = null,
    var httpErrorRetries: Int? = null,
    var p2pErrorRetries: Int? = null,
    var announceTrackers: List<String>? = null,
    var rtcConfig: RtcConfig? = null,
    var trackerClientVersionPrefix: String? = null,
    var swarmId: String? = null,

    @Transient var validateP2PSegmentJs: String? = null,
    @Transient var validateHTTPSegmentJs: String? = null,
    @Transient var httpRequestSetupJs: String? = null
)

@Suppress("LongParameterList")
@Serializable
class CoreConfig(
    var segmentMemoryStorageLimit: Int? = null,
    @Transient var customSegmentStorageFactoryJs: String? = null,

    var isP2PUploadDisabled: Boolean? = null,
    var isP2PDisabled: Boolean? = null,
    var highDemandTimeWindow: Int? = null,
    var httpDownloadTimeWindow: Int? = null,
    var p2pDownloadTimeWindow: Int? = null,
    var simultaneousHttpDownloads: Int? = null,
    var simultaneousP2PDownloads: Int? = null,
    var webRtcMaxMessageSize: Int? = null,
    var p2pNotReceivingBytesTimeoutMs: Int? = null,
    var p2pInactiveLoaderDestroyTimeoutMs: Int? = null,
    var httpNotReceivingBytesTimeoutMs: Int? = null,
    var httpErrorRetries: Int? = null,
    var p2pErrorRetries: Int? = null,
    var announceTrackers: List<String>? = null,
    var rtcConfig: RtcConfig? = null,
    var trackerClientVersionPrefix: String? = null,
    var swarmId: String? = null,

    @Transient var validateP2PSegmentJs: String? = null,
    @Transient var validateHTTPSegmentJs: String? = null,
    @Transient var httpRequestSetupJs: String? = null,

    var mainStream: StreamConfig? = null,
    var secondaryStream: StreamConfig? = null
)

@Suppress("LongParameterList")
@Serializable
class DynamicStreamConfig(
    var highDemandTimeWindow: Int? = null,
    var httpDownloadTimeWindow: Int? = null,
    var p2pDownloadTimeWindow: Int? = null,
    var simultaneousHttpDownloads: Int? = null,
    var simultaneousP2PDownloads: Int? = null,
    var webRtcMaxMessageSize: Int? = null,
    var p2pNotReceivingBytesTimeoutMs: Int? = null,
    var p2pInactiveLoaderDestroyTimeoutMs: Int? = null,
    var httpNotReceivingBytesTimeoutMs: Int? = null,
    var httpErrorRetries: Int? = null,
    var p2pErrorRetries: Int? = null,
    var isP2PDisabled: Boolean? = null,
    var isP2PUploadDisabled: Boolean? = null,

    @Transient var validateP2PSegmentJs: String? = null,
    @Transient var httpRequestSetupJs: String? = null
)

@Suppress("LongParameterList")
@Serializable
class DynamicCoreConfig(
    var segmentMemoryStorageLimit: Int? = null,
    @Transient var customSegmentStorageFactoryJs: String? = null,

    var highDemandTimeWindow: Int? = null,
    var httpDownloadTimeWindow: Int? = null,
    var p2pDownloadTimeWindow: Int? = null,
    var simultaneousHttpDownloads: Int? = null,
    var simultaneousP2PDownloads: Int? = null,
    var webRtcMaxMessageSize: Int? = null,
    var p2pNotReceivingBytesTimeoutMs: Int? = null,
    var p2pInactiveLoaderDestroyTimeoutMs: Int? = null,
    var httpNotReceivingBytesTimeoutMs: Int? = null,
    var httpErrorRetries: Int? = null,
    var p2pErrorRetries: Int? = null,
    var isP2PDisabled: Boolean? = null,
    var isP2PUploadDisabled: Boolean? = null,

    @Transient var validateP2PSegmentJs: String? = null,
    @Transient var httpRequestSetupJs: String? = null,

    var mainStream: DynamicStreamConfig? = null,
    var secondaryStream: DynamicStreamConfig? = null
)
