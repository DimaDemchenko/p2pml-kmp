package com.novage.p2pml.internal.engine

import com.novage.p2pml.api.config.CoreConfig
import com.novage.p2pml.api.config.DynamicCoreConfig
import kotlinx.serialization.json.Json

internal object CoreConfigJsMapper {
    private val p2pConfigJson = Json {
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    private const val VALIDATE_P2P = "validateP2PSegment"
    private const val VALIDATE_HTTP = "validateHTTPSegment"
    private const val HTTP_SETUP = "httpRequestSetup"

    private class StreamScope(val path: String, val functions: List<Pair<String, String>>)

    fun toJsExpression(config: CoreConfig): String = buildConfigExpression(
        configJson = p2pConfigJson.encodeToString(config),
        customSegmentStorageFactoryJs = config.customSegmentStorageFactoryJs,
        streamScopes = listOfNotNull(
            streamScope(
                "config",
                VALIDATE_P2P to config.validateP2PSegmentJs,
                VALIDATE_HTTP to config.validateHTTPSegmentJs,
                HTTP_SETUP to config.httpRequestSetupJs
            ),
            config.mainStream?.let {
                streamScope(
                    "config.mainStream",
                    VALIDATE_P2P to it.validateP2PSegmentJs,
                    VALIDATE_HTTP to it.validateHTTPSegmentJs,
                    HTTP_SETUP to it.httpRequestSetupJs
                )
            },
            config.secondaryStream?.let {
                streamScope(
                    "config.secondaryStream",
                    VALIDATE_P2P to it.validateP2PSegmentJs,
                    VALIDATE_HTTP to it.validateHTTPSegmentJs,
                    HTTP_SETUP to it.httpRequestSetupJs
                )
            }
        )
    )

    fun toJsExpression(config: DynamicCoreConfig): String = buildConfigExpression(
        configJson = p2pConfigJson.encodeToString(config),
        customSegmentStorageFactoryJs = config.customSegmentStorageFactoryJs,
        streamScopes = listOfNotNull(
            streamScope(
                "config",
                VALIDATE_P2P to config.validateP2PSegmentJs,
                VALIDATE_HTTP to config.validateHTTPSegmentJs,
                HTTP_SETUP to config.httpRequestSetupJs
            ),
            config.mainStream?.let {
                streamScope(
                    "config.mainStream",
                    VALIDATE_P2P to it.validateP2PSegmentJs,
                    VALIDATE_HTTP to it.validateHTTPSegmentJs,
                    HTTP_SETUP to it.httpRequestSetupJs
                )
            },
            config.secondaryStream?.let {
                streamScope(
                    "config.secondaryStream",
                    VALIDATE_P2P to it.validateP2PSegmentJs,
                    VALIDATE_HTTP to it.validateHTTPSegmentJs,
                    HTTP_SETUP to it.httpRequestSetupJs
                )
            }
        )
    )

    private fun streamScope(path: String, vararg functions: Pair<String, String?>): StreamScope? {
        val present = functions.mapNotNull { (property, value) -> value?.let { property to it } }
        return if (present.isEmpty()) null else StreamScope(path, present)
    }

    private fun buildConfigExpression(
        configJson: String,
        customSegmentStorageFactoryJs: String?,
        streamScopes: List<StreamScope>
    ): String = buildString {
        appendLine("(function() {")
        appendLine("  var config = $configJson;")
        customSegmentStorageFactoryJs?.let { appendLine("  config.customSegmentStorageFactory = $it;") }

        for (scope in streamScopes) {
            if (scope.path != "config") appendLine("  ${scope.path} = ${scope.path} || {};")
            for ((property, value) in scope.functions) {
                appendLine("  ${scope.path}.$property = $value;")
            }
        }

        appendLine("  return config;")
        append("})()")
    }
}
