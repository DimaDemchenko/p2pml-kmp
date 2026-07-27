package com.novage.p2pml.internal.engine

import com.novage.p2pml.api.config.CoreConfig
import com.novage.p2pml.api.config.DynamicCoreConfig

internal object CoreConfigJsMapper {
    private const val VALIDATE_P2P = "validateP2PSegment"
    private const val VALIDATE_HTTP = "validateHTTPSegment"
    private const val HTTP_SETUP = "httpRequestSetup"

    private class StreamScope(val path: String, val functions: List<Pair<String, String>>)

    private class StreamFunctions(val validateP2P: String?, val validateHttp: String?, val httpSetup: String?)

    fun toJsExpression(config: CoreConfig): String = buildConfigExpression(
        configJson = engineBridgeJson.encodeToString(config),
        customSegmentStorageFactoryJs = config.customSegmentStorageFactoryJs,
        streamScopes = streamScopes(
            top = StreamFunctions(config.validateP2PSegmentJs, config.validateHTTPSegmentJs, config.httpRequestSetupJs),
            mainStream = config.mainStream?.let {
                StreamFunctions(it.validateP2PSegmentJs, it.validateHTTPSegmentJs, it.httpRequestSetupJs)
            },
            secondaryStream = config.secondaryStream?.let {
                StreamFunctions(it.validateP2PSegmentJs, it.validateHTTPSegmentJs, it.httpRequestSetupJs)
            }
        )
    )

    fun toJsExpression(config: DynamicCoreConfig): String = buildConfigExpression(
        configJson = engineBridgeJson.encodeToString(config),
        customSegmentStorageFactoryJs = config.customSegmentStorageFactoryJs,
        streamScopes = streamScopes(
            top = StreamFunctions(config.validateP2PSegmentJs, config.validateHTTPSegmentJs, config.httpRequestSetupJs),
            mainStream = config.mainStream?.let {
                StreamFunctions(it.validateP2PSegmentJs, it.validateHTTPSegmentJs, it.httpRequestSetupJs)
            },
            secondaryStream = config.secondaryStream?.let {
                StreamFunctions(it.validateP2PSegmentJs, it.validateHTTPSegmentJs, it.httpRequestSetupJs)
            }
        )
    )

    private fun streamScopes(
        top: StreamFunctions,
        mainStream: StreamFunctions?,
        secondaryStream: StreamFunctions?
    ): List<StreamScope> = listOfNotNull(
        top.scopeAt("config"),
        mainStream?.scopeAt("config.mainStream"),
        secondaryStream?.scopeAt("config.secondaryStream")
    )

    private fun StreamFunctions.scopeAt(path: String): StreamScope? = streamScope(
        path,
        VALIDATE_P2P to validateP2P,
        VALIDATE_HTTP to validateHttp,
        HTTP_SETUP to httpSetup
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
