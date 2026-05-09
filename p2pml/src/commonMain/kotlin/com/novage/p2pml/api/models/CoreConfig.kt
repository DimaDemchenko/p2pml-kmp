package com.novage.p2pml.api.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class IceServer(val urls: List<String>, val username: String? = null, val credential: String? = null)

@Serializable
data class RtcConfig(val iceServers: List<IceServer>? = null)

@Serializable
data class StreamConfig(
    val isP2PUploadDisabled: Boolean? = null,
    val isP2PDisabled: Boolean? = null,
    val highDemandTimeWindow: Int? = null,
    val httpDownloadTimeWindow: Int? = null,
    val p2pDownloadTimeWindow: Int? = null,
    val simultaneousHttpDownloads: Int? = null,
    val simultaneousP2PDownloads: Int? = null,
    val webRtcMaxMessageSize: Int? = null,
    val p2pNotReceivingBytesTimeoutMs: Int? = null,
    val p2pInactiveLoaderDestroyTimeoutMs: Int? = null,
    val httpNotReceivingBytesTimeoutMs: Int? = null,
    val httpErrorRetries: Int? = null,
    val p2pErrorRetries: Int? = null,
    val announceTrackers: List<String>? = null,
    val rtcConfig: RtcConfig? = null,
    val trackerClientVersionPrefix: String? = null,
    val swarmId: String? = null,

    @Transient val validateP2PSegmentJs: String? = null,
    @Transient val validateHTTPSegmentJs: String? = null,
    @Transient val httpRequestSetupJs: String? = null
)

@Serializable
data class CoreConfig(
    val segmentMemoryStorageLimit: Int? = null,
    @Transient val customSegmentStorageFactoryJs: String? = null,

    val isP2PUploadDisabled: Boolean? = null,
    val isP2PDisabled: Boolean? = null,
    val highDemandTimeWindow: Int? = null,
    val httpDownloadTimeWindow: Int? = null,
    val p2pDownloadTimeWindow: Int? = null,
    val simultaneousHttpDownloads: Int? = null,
    val simultaneousP2PDownloads: Int? = null,
    val webRtcMaxMessageSize: Int? = null,
    val p2pNotReceivingBytesTimeoutMs: Int? = null,
    val p2pInactiveLoaderDestroyTimeoutMs: Int? = null,
    val httpNotReceivingBytesTimeoutMs: Int? = null,
    val httpErrorRetries: Int? = null,
    val p2pErrorRetries: Int? = null,
    val announceTrackers: List<String>? = null,
    val rtcConfig: RtcConfig? = null,
    val trackerClientVersionPrefix: String? = null,
    val swarmId: String? = null,

    @Transient val validateP2PSegmentJs: String? = null,
    @Transient val validateHTTPSegmentJs: String? = null,
    @Transient val httpRequestSetupJs: String? = null,

    val mainStream: StreamConfig? = null,
    val secondaryStream: StreamConfig? = null
)

@Serializable
data class DynamicStreamConfig(
    val highDemandTimeWindow: Int? = null,
    val httpDownloadTimeWindow: Int? = null,
    val p2pDownloadTimeWindow: Int? = null,
    val simultaneousHttpDownloads: Int? = null,
    val simultaneousP2PDownloads: Int? = null,
    val webRtcMaxMessageSize: Int? = null,
    val p2pNotReceivingBytesTimeoutMs: Int? = null,
    val p2pInactiveLoaderDestroyTimeoutMs: Int? = null,
    val httpNotReceivingBytesTimeoutMs: Int? = null,
    val httpErrorRetries: Int? = null,
    val p2pErrorRetries: Int? = null,
    val isP2PDisabled: Boolean? = null,
    val isP2PUploadDisabled: Boolean? = null,

    @Transient val validateP2PSegmentJs: String? = null,
    @Transient val httpRequestSetupJs: String? = null
)

@Serializable
data class DynamicCoreConfig(
    val segmentMemoryStorageLimit: Int? = null,
    @Transient val customSegmentStorageFactoryJs: String? = null,

    val highDemandTimeWindow: Int? = null,
    val httpDownloadTimeWindow: Int? = null,
    val p2pDownloadTimeWindow: Int? = null,
    val simultaneousHttpDownloads: Int? = null,
    val simultaneousP2PDownloads: Int? = null,
    val webRtcMaxMessageSize: Int? = null,
    val p2pNotReceivingBytesTimeoutMs: Int? = null,
    val p2pInactiveLoaderDestroyTimeoutMs: Int? = null,
    val httpNotReceivingBytesTimeoutMs: Int? = null,
    val httpErrorRetries: Int? = null,
    val p2pErrorRetries: Int? = null,
    val isP2PDisabled: Boolean? = null,
    val isP2PUploadDisabled: Boolean? = null,

    @Transient val validateP2PSegmentJs: String? = null,
    @Transient val httpRequestSetupJs: String? = null,

    val mainStream: DynamicStreamConfig? = null,
    val secondaryStream: DynamicStreamConfig? = null
)

class StreamConfigBuilder {
    private var isP2PUploadDisabled: Boolean? = null
    private var isP2PDisabled: Boolean? = null
    private var highDemandTimeWindow: Int? = null
    private var httpDownloadTimeWindow: Int? = null
    private var p2pDownloadTimeWindow: Int? = null
    private var simultaneousHttpDownloads: Int? = null
    private var simultaneousP2PDownloads: Int? = null
    private var webRtcMaxMessageSize: Int? = null
    private var p2pNotReceivingBytesTimeoutMs: Int? = null
    private var p2pInactiveLoaderDestroyTimeoutMs: Int? = null
    private var httpNotReceivingBytesTimeoutMs: Int? = null
    private var httpErrorRetries: Int? = null
    private var p2pErrorRetries: Int? = null
    private var announceTrackers: List<String>? = null
    private var rtcConfig: RtcConfig? = null
    private var trackerClientVersionPrefix: String? = null
    private var swarmId: String? = null
    private var validateP2PSegmentJs: String? = null
    private var validateHTTPSegmentJs: String? = null
    private var httpRequestSetupJs: String? = null

    fun isP2PUploadDisabled(value: Boolean) = apply { this.isP2PUploadDisabled = value }
    fun isP2PDisabled(value: Boolean) = apply { this.isP2PDisabled = value }
    fun highDemandTimeWindow(value: Int) = apply { this.highDemandTimeWindow = value }
    fun httpDownloadTimeWindow(value: Int) = apply { this.httpDownloadTimeWindow = value }
    fun p2pDownloadTimeWindow(value: Int) = apply { this.p2pDownloadTimeWindow = value }
    fun simultaneousHttpDownloads(value: Int) = apply { this.simultaneousHttpDownloads = value }
    fun simultaneousP2PDownloads(value: Int) = apply { this.simultaneousP2PDownloads = value }
    fun webRtcMaxMessageSize(value: Int) = apply { this.webRtcMaxMessageSize = value }
    fun p2pNotReceivingBytesTimeoutMs(value: Int) = apply { this.p2pNotReceivingBytesTimeoutMs = value }
    fun p2pInactiveLoaderDestroyTimeoutMs(value: Int) = apply { this.p2pInactiveLoaderDestroyTimeoutMs = value }
    fun httpNotReceivingBytesTimeoutMs(value: Int) = apply { this.httpNotReceivingBytesTimeoutMs = value }
    fun httpErrorRetries(value: Int) = apply { this.httpErrorRetries = value }
    fun p2pErrorRetries(value: Int) = apply { this.p2pErrorRetries = value }
    fun announceTrackers(value: List<String>) = apply { this.announceTrackers = value }
    fun rtcConfig(value: RtcConfig) = apply { this.rtcConfig = value }
    fun trackerClientVersionPrefix(value: String) = apply { this.trackerClientVersionPrefix = value }
    fun swarmId(value: String) = apply { this.swarmId = value }
    fun validateP2PSegmentJs(value: String) = apply { this.validateP2PSegmentJs = value }
    fun validateHTTPSegmentJs(value: String) = apply { this.validateHTTPSegmentJs = value }
    fun httpRequestSetupJs(value: String) = apply { this.httpRequestSetupJs = value }

    fun build() = StreamConfig(
        isP2PUploadDisabled, isP2PDisabled, highDemandTimeWindow, httpDownloadTimeWindow,
        p2pDownloadTimeWindow, simultaneousHttpDownloads, simultaneousP2PDownloads,
        webRtcMaxMessageSize, p2pNotReceivingBytesTimeoutMs, p2pInactiveLoaderDestroyTimeoutMs,
        httpNotReceivingBytesTimeoutMs, httpErrorRetries, p2pErrorRetries, announceTrackers,
        rtcConfig, trackerClientVersionPrefix, swarmId, validateP2PSegmentJs,
        validateHTTPSegmentJs, httpRequestSetupJs
    )
}

@Suppress("TooManyFunctions")
class CoreConfigBuilder {
    private var segmentMemoryStorageLimit: Int? = null
    private var customSegmentStorageFactoryJs: String? = null
    private var isP2PUploadDisabled: Boolean? = null
    private var isP2PDisabled: Boolean? = null
    private var highDemandTimeWindow: Int? = null
    private var httpDownloadTimeWindow: Int? = null
    private var p2pDownloadTimeWindow: Int? = null
    private var simultaneousHttpDownloads: Int? = null
    private var simultaneousP2PDownloads: Int? = null
    private var webRtcMaxMessageSize: Int? = null
    private var p2pNotReceivingBytesTimeoutMs: Int? = null
    private var p2pInactiveLoaderDestroyTimeoutMs: Int? = null
    private var httpNotReceivingBytesTimeoutMs: Int? = null
    private var httpErrorRetries: Int? = null
    private var p2pErrorRetries: Int? = null
    private var announceTrackers: List<String>? = null
    private var rtcConfig: RtcConfig? = null
    private var trackerClientVersionPrefix: String? = null
    private var swarmId: String? = null
    private var validateP2PSegmentJs: String? = null
    private var validateHTTPSegmentJs: String? = null
    private var httpRequestSetupJs: String? = null
    private var mainStream: StreamConfig? = null
    private var secondaryStream: StreamConfig? = null

    fun segmentMemoryStorageLimit(value: Int) = apply { this.segmentMemoryStorageLimit = value }
    fun customSegmentStorageFactoryJs(value: String) = apply { this.customSegmentStorageFactoryJs = value }
    fun isP2PUploadDisabled(value: Boolean) = apply { this.isP2PUploadDisabled = value }
    fun isP2PDisabled(value: Boolean) = apply { this.isP2PDisabled = value }
    fun highDemandTimeWindow(value: Int) = apply { this.highDemandTimeWindow = value }
    fun httpDownloadTimeWindow(value: Int) = apply { this.httpDownloadTimeWindow = value }
    fun p2pDownloadTimeWindow(value: Int) = apply { this.p2pDownloadTimeWindow = value }
    fun simultaneousHttpDownloads(value: Int) = apply { this.simultaneousHttpDownloads = value }
    fun simultaneousP2PDownloads(value: Int) = apply { this.simultaneousP2PDownloads = value }
    fun webRtcMaxMessageSize(value: Int) = apply { this.webRtcMaxMessageSize = value }
    fun p2pNotReceivingBytesTimeoutMs(value: Int) = apply { this.p2pNotReceivingBytesTimeoutMs = value }
    fun p2pInactiveLoaderDestroyTimeoutMs(value: Int) = apply { this.p2pInactiveLoaderDestroyTimeoutMs = value }
    fun httpNotReceivingBytesTimeoutMs(value: Int) = apply { this.httpNotReceivingBytesTimeoutMs = value }
    fun httpErrorRetries(value: Int) = apply { this.httpErrorRetries = value }
    fun p2pErrorRetries(value: Int) = apply { this.p2pErrorRetries = value }
    fun announceTrackers(value: List<String>) = apply { this.announceTrackers = value }
    fun rtcConfig(value: RtcConfig) = apply { this.rtcConfig = value }
    fun trackerClientVersionPrefix(value: String) = apply { this.trackerClientVersionPrefix = value }
    fun swarmId(value: String) = apply { this.swarmId = value }
    fun validateP2PSegmentJs(value: String) = apply { this.validateP2PSegmentJs = value }
    fun validateHTTPSegmentJs(value: String) = apply { this.validateHTTPSegmentJs = value }
    fun httpRequestSetupJs(value: String) = apply { this.httpRequestSetupJs = value }
    fun mainStream(value: StreamConfig) = apply { this.mainStream = value }
    fun secondaryStream(value: StreamConfig) = apply { this.secondaryStream = value }

    fun build() = CoreConfig(
        segmentMemoryStorageLimit, customSegmentStorageFactoryJs, isP2PUploadDisabled, isP2PDisabled,
        highDemandTimeWindow, httpDownloadTimeWindow, p2pDownloadTimeWindow, simultaneousHttpDownloads,
        simultaneousP2PDownloads, webRtcMaxMessageSize, p2pNotReceivingBytesTimeoutMs,
        p2pInactiveLoaderDestroyTimeoutMs, httpNotReceivingBytesTimeoutMs, httpErrorRetries,
        p2pErrorRetries, announceTrackers, rtcConfig, trackerClientVersionPrefix, swarmId,
        validateP2PSegmentJs, validateHTTPSegmentJs, httpRequestSetupJs, mainStream, secondaryStream
    )
}

class DynamicStreamConfigBuilder {
    private var highDemandTimeWindow: Int? = null
    private var httpDownloadTimeWindow: Int? = null
    private var p2pDownloadTimeWindow: Int? = null
    private var simultaneousHttpDownloads: Int? = null
    private var simultaneousP2PDownloads: Int? = null
    private var webRtcMaxMessageSize: Int? = null
    private var p2pNotReceivingBytesTimeoutMs: Int? = null
    private var p2pInactiveLoaderDestroyTimeoutMs: Int? = null
    private var httpNotReceivingBytesTimeoutMs: Int? = null
    private var httpErrorRetries: Int? = null
    private var p2pErrorRetries: Int? = null
    private var isP2PDisabled: Boolean? = null
    private var isP2PUploadDisabled: Boolean? = null
    private var validateP2PSegmentJs: String? = null
    private var httpRequestSetupJs: String? = null

    fun highDemandTimeWindow(value: Int) = apply { this.highDemandTimeWindow = value }
    fun httpDownloadTimeWindow(value: Int) = apply { this.httpDownloadTimeWindow = value }
    fun p2pDownloadTimeWindow(value: Int) = apply { this.p2pDownloadTimeWindow = value }
    fun simultaneousHttpDownloads(value: Int) = apply { this.simultaneousHttpDownloads = value }
    fun simultaneousP2PDownloads(value: Int) = apply { this.simultaneousP2PDownloads = value }
    fun webRtcMaxMessageSize(value: Int) = apply { this.webRtcMaxMessageSize = value }
    fun p2pNotReceivingBytesTimeoutMs(value: Int) = apply { this.p2pNotReceivingBytesTimeoutMs = value }
    fun p2pInactiveLoaderDestroyTimeoutMs(value: Int) = apply { this.p2pInactiveLoaderDestroyTimeoutMs = value }
    fun httpNotReceivingBytesTimeoutMs(value: Int) = apply { this.httpNotReceivingBytesTimeoutMs = value }
    fun httpErrorRetries(value: Int) = apply { this.httpErrorRetries = value }
    fun p2pErrorRetries(value: Int) = apply { this.p2pErrorRetries = value }
    fun isP2PDisabled(value: Boolean) = apply { this.isP2PDisabled = value }
    fun isP2PUploadDisabled(value: Boolean) = apply { this.isP2PUploadDisabled = value }
    fun validateP2PSegmentJs(value: String) = apply { this.validateP2PSegmentJs = value }
    fun httpRequestSetupJs(value: String) = apply { this.httpRequestSetupJs = value }

    fun build() = DynamicStreamConfig(
        highDemandTimeWindow, httpDownloadTimeWindow, p2pDownloadTimeWindow, simultaneousHttpDownloads,
        simultaneousP2PDownloads, webRtcMaxMessageSize, p2pNotReceivingBytesTimeoutMs,
        p2pInactiveLoaderDestroyTimeoutMs, httpNotReceivingBytesTimeoutMs, httpErrorRetries,
        p2pErrorRetries, isP2PDisabled, isP2PUploadDisabled, validateP2PSegmentJs, httpRequestSetupJs
    )
}

class DynamicCoreConfigBuilder {
    private var segmentMemoryStorageLimit: Int? = null
    private var customSegmentStorageFactoryJs: String? = null
    private var highDemandTimeWindow: Int? = null
    private var httpDownloadTimeWindow: Int? = null
    private var p2pDownloadTimeWindow: Int? = null
    private var simultaneousHttpDownloads: Int? = null
    private var simultaneousP2PDownloads: Int? = null
    private var webRtcMaxMessageSize: Int? = null
    private var p2pNotReceivingBytesTimeoutMs: Int? = null
    private var p2pInactiveLoaderDestroyTimeoutMs: Int? = null
    private var httpNotReceivingBytesTimeoutMs: Int? = null
    private var httpErrorRetries: Int? = null
    private var p2pErrorRetries: Int? = null
    private var isP2PDisabled: Boolean? = null
    private var isP2PUploadDisabled: Boolean? = null
    private var validateP2PSegmentJs: String? = null
    private var httpRequestSetupJs: String? = null
    private var mainStream: DynamicStreamConfig? = null
    private var secondaryStream: DynamicStreamConfig? = null

    fun segmentMemoryStorageLimit(value: Int) = apply { this.segmentMemoryStorageLimit = value }
    fun customSegmentStorageFactoryJs(value: String) = apply { this.customSegmentStorageFactoryJs = value }
    fun highDemandTimeWindow(value: Int) = apply { this.highDemandTimeWindow = value }
    fun httpDownloadTimeWindow(value: Int) = apply { this.httpDownloadTimeWindow = value }
    fun p2pDownloadTimeWindow(value: Int) = apply { this.p2pDownloadTimeWindow = value }
    fun simultaneousHttpDownloads(value: Int) = apply { this.simultaneousHttpDownloads = value }
    fun simultaneousP2PDownloads(value: Int) = apply { this.simultaneousP2PDownloads = value }
    fun webRtcMaxMessageSize(value: Int) = apply { this.webRtcMaxMessageSize = value }
    fun p2pNotReceivingBytesTimeoutMs(value: Int) = apply { this.p2pNotReceivingBytesTimeoutMs = value }
    fun p2pInactiveLoaderDestroyTimeoutMs(value: Int) = apply { this.p2pInactiveLoaderDestroyTimeoutMs = value }
    fun httpNotReceivingBytesTimeoutMs(value: Int) = apply { this.httpNotReceivingBytesTimeoutMs = value }
    fun httpErrorRetries(value: Int) = apply { this.httpErrorRetries = value }
    fun p2pErrorRetries(value: Int) = apply { this.p2pErrorRetries = value }
    fun isP2PDisabled(value: Boolean) = apply { this.isP2PDisabled = value }
    fun isP2PUploadDisabled(value: Boolean) = apply { this.isP2PUploadDisabled = value }
    fun validateP2PSegmentJs(value: String) = apply { this.validateP2PSegmentJs = value }
    fun httpRequestSetupJs(value: String) = apply { this.httpRequestSetupJs = value }
    fun mainStream(value: DynamicStreamConfig) = apply { this.mainStream = value }
    fun secondaryStream(value: DynamicStreamConfig) = apply { this.secondaryStream = value }

    fun build() = DynamicCoreConfig(
        segmentMemoryStorageLimit, customSegmentStorageFactoryJs, highDemandTimeWindow, httpDownloadTimeWindow,
        p2pDownloadTimeWindow, simultaneousHttpDownloads, simultaneousP2PDownloads, webRtcMaxMessageSize,
        p2pNotReceivingBytesTimeoutMs, p2pInactiveLoaderDestroyTimeoutMs, httpNotReceivingBytesTimeoutMs,
        httpErrorRetries, p2pErrorRetries, isP2PDisabled, isP2PUploadDisabled, validateP2PSegmentJs,
        httpRequestSetupJs, mainStream, secondaryStream
    )
}
