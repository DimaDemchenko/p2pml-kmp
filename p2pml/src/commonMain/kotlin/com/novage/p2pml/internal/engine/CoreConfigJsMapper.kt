package com.novage.p2pml.internal.engine

import com.novage.p2pml.api.models.CoreConfig
import com.novage.p2pml.api.models.DynamicCoreConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object CoreConfigJsMapper {
    private val p2pConfigJson = Json {
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun toJsExpression(config: CoreConfig): String {
        val configJson = p2pConfigJson.encodeToString(config)
        return buildString {
            appendLine("(function() {")
            appendLine("  var config = $configJson;")
            config.customSegmentStorageFactoryJs?.let { appendLine("  config.customSegmentStorageFactory = $it;") }

            fun appendStreamFunctions(path: String, validateP2P: String?, validateHTTP: String?, setupHTTP: String?) {
                if (validateP2P == null && validateHTTP == null && setupHTTP == null) return
                if (path != "config") appendLine("  $path = $path || {};")
                validateP2P?.let { appendLine("  $path.validateP2PSegment = $it;") }
                validateHTTP?.let { appendLine("  $path.validateHTTPSegment = $it;") }
                setupHTTP?.let { appendLine("  $path.httpRequestSetup = $it;") }
            }

            appendStreamFunctions("config", config.validateP2PSegmentJs, config.validateHTTPSegmentJs, config.httpRequestSetupJs)
            config.mainStream?.let {
                appendStreamFunctions(
                    "config.mainStream",
                    it.validateP2PSegmentJs,
                    it.validateHTTPSegmentJs,
                    it.httpRequestSetupJs
                )
            }
            config.secondaryStream?.let {
                appendStreamFunctions(
                    "config.secondaryStream",
                    it.validateP2PSegmentJs,
                    it.validateHTTPSegmentJs,
                    it.httpRequestSetupJs
                )
            }

            appendLine("  return config;")
            append("})()")
        }
    }

    fun toJsExpression(config: DynamicCoreConfig): String {
        val configJson = p2pConfigJson.encodeToString(config)
        return buildString {
            appendLine("(function() {")
            appendLine("  var config = $configJson;")
            config.customSegmentStorageFactoryJs?.let { appendLine("  config.customSegmentStorageFactory = $it;") }

            fun appendDynamicStreamFunctions(path: String, validateP2P: String?, setupHTTP: String?) {
                if (validateP2P == null && setupHTTP == null) return
                if (path != "config") appendLine("  $path = $path || {};")
                validateP2P?.let { appendLine("  $path.validateP2PSegment = $it;") }
                setupHTTP?.let { appendLine("  $path.httpRequestSetup = $it;") }
            }

            appendDynamicStreamFunctions("config", config.validateP2PSegmentJs, config.httpRequestSetupJs)

            config.mainStream?.let {
                appendDynamicStreamFunctions("config.mainStream", it.validateP2PSegmentJs, it.httpRequestSetupJs)
            }
            config.secondaryStream?.let {
                appendDynamicStreamFunctions("config.secondaryStream", it.validateP2PSegmentJs, it.httpRequestSetupJs)
            }

            appendLine("  return config;")
            append("})()")
        }
    }
}
