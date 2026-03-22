package com.novage.p2pml.api.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

@Serializable
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

@Serializable
data class RtcConfig(
    val iceServers: List<IceServer>? = null
)

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

private val p2pConfigJson = Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
}

internal fun CoreConfig.toJsExpression(): String {
    val configJson = p2pConfigJson.encodeToString(this)

    return buildString {
        appendLine("(function() {")
        appendLine("  var config = $configJson;")

        customSegmentStorageFactoryJs?.let {
            appendLine("  config.customSegmentStorageFactory = $it;")
        }

        fun appendStreamFunctions(
            path: String,
            validateP2P: String?,
            validateHTTP: String?,
            setupHTTP: String?
        ) {
            if (validateP2P == null && validateHTTP == null && setupHTTP == null) return

            if (path != "config") {
                appendLine("  $path = $path || {};")
            }

            validateP2P?.let { appendLine("  $path.validateP2PSegment = $it;") }
            validateHTTP?.let { appendLine("  $path.validateHTTPSegment = $it;") }
            setupHTTP?.let { appendLine("  $path.httpRequestSetup = $it;") }
        }

        appendStreamFunctions(
            path = "config",
            validateP2P = validateP2PSegmentJs,
            validateHTTP = validateHTTPSegmentJs,
            setupHTTP = httpRequestSetupJs
        )

        mainStream?.let { stream ->
            appendStreamFunctions(
                path = "config.mainStream",
                validateP2P = stream.validateP2PSegmentJs,
                validateHTTP = stream.validateHTTPSegmentJs,
                setupHTTP = stream.httpRequestSetupJs
            )
        }

        secondaryStream?.let { stream ->
            appendStreamFunctions(
                path = "config.secondaryStream",
                validateP2P = stream.validateP2PSegmentJs,
                validateHTTP = stream.validateHTTPSegmentJs,
                setupHTTP = stream.httpRequestSetupJs
            )
        }

        appendLine("  return config;")
        append("})()")
    }
}

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


internal fun DynamicCoreConfig.toJsExpression(): String {
    val configJson = p2pConfigJson.encodeToString(this)

    return buildString {
        appendLine("(function() {")
        appendLine("  var config = $configJson;")

        customSegmentStorageFactoryJs?.let {
            appendLine("  config.customSegmentStorageFactory = $it;")
        }

        fun appendDynamicStreamFunctions(
            path: String,
            validateP2P: String?,
            setupHTTP: String?
        ) {
            if (validateP2P == null && setupHTTP == null) return

            if (path != "config") {
                appendLine("  $path = $path || {};")
            }

            validateP2P?.let { appendLine("  $path.validateP2PSegment = $it;") }
            setupHTTP?.let { appendLine("  $path.httpRequestSetup = $it;") }
        }

        appendDynamicStreamFunctions(
            path = "config",
            validateP2P = validateP2PSegmentJs,
            setupHTTP = httpRequestSetupJs
        )

        mainStream?.let { stream ->
            appendDynamicStreamFunctions(
                path = "config.mainStream",
                validateP2P = stream.validateP2PSegmentJs,
                setupHTTP = stream.httpRequestSetupJs
            )
        }

        secondaryStream?.let { stream ->
            appendDynamicStreamFunctions(
                path = "config.secondaryStream",
                validateP2P = stream.validateP2PSegmentJs,
                setupHTTP = stream.httpRequestSetupJs
            )
        }

        appendLine("  return config;")
        append("})()")
    }
}
