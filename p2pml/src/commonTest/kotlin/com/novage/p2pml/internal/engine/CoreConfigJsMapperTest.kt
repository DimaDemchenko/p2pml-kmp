package com.novage.p2pml.internal.engine

import com.novage.p2pml.api.config.CoreConfig
import com.novage.p2pml.api.config.DynamicCoreConfig
import com.novage.p2pml.api.config.DynamicStreamConfig
import com.novage.p2pml.api.config.StreamConfig
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreConfigJsMapperTest {

    @Test
    fun emptyCoreConfigEmitsBareIife() {
        val js = CoreConfigJsMapper.toJsExpression(CoreConfig())

        assertTrue(js.startsWith("(function() {"), "expected IIFE wrapper, got: $js")
        assertTrue(js.trimEnd().endsWith("})()"), "expected IIFE close, got: $js")
        assertContains(js, "var config = {};")
        assertContains(js, "return config;")
        assertFalse(js.contains("validateP2PSegment"), "no functions expected")
        assertFalse(js.contains("|| {}"), "no stream guards expected")
    }

    @Test
    fun coreConfigEmitsTopLevelAndStreamFunctionsInOrder() {
        val config = CoreConfig().apply {
            customSegmentStorageFactoryJs = "FACTORY_FN"
            validateP2PSegmentJs = "P2P_FN"
            validateHTTPSegmentJs = "HTTP_FN"
            httpRequestSetupJs = "SETUP_FN"
            mainStream = StreamConfig().apply { validateP2PSegmentJs = "MAIN_P2P_FN" }
        }

        val js = CoreConfigJsMapper.toJsExpression(config)

        assertContains(js, "config.customSegmentStorageFactory = FACTORY_FN;")
        assertContains(js, "config.validateP2PSegment = P2P_FN;")
        assertContains(js, "config.validateHTTPSegment = HTTP_FN;")
        assertContains(js, "config.httpRequestSetup = SETUP_FN;")

        // Top-level scope must NOT be guarded (config is already declared); nested streams must be.
        assertFalse(js.contains("config = config || {};"), "top-level config must not be guarded")
        assertContains(js, "config.mainStream = config.mainStream || {};")
        assertContains(js, "config.mainStream.validateP2PSegment = MAIN_P2P_FN;")

        // Deterministic emission order within a scope: P2P -> HTTP -> setup.
        assertTrue(
            js.indexOf("validateP2PSegment") < js.indexOf("validateHTTPSegment") &&
                js.indexOf("validateHTTPSegment") < js.indexOf("httpRequestSetup"),
            "functions must be emitted in P2P -> HTTP -> setup order"
        )
    }

    @Test
    fun dynamicConfigEmitsAllFunctionHooks() {
        val config = DynamicCoreConfig().apply {
            validateP2PSegmentJs = "P2P_FN"
            validateHTTPSegmentJs = "HTTP_FN"
            httpRequestSetupJs = "SETUP_FN"
            mainStream = DynamicStreamConfig().apply { validateHTTPSegmentJs = "MAIN_HTTP_FN" }
        }

        val js = CoreConfigJsMapper.toJsExpression(config)

        assertContains(js, "config.validateP2PSegment = P2P_FN;")
        assertContains(js, "config.validateHTTPSegment = HTTP_FN;")
        assertContains(js, "config.httpRequestSetup = SETUP_FN;")
        assertContains(js, "config.mainStream = config.mainStream || {};")
        assertContains(js, "config.mainStream.validateHTTPSegment = MAIN_HTTP_FN;")
    }

    @Test
    fun assignedBooleansAlwaysSerializeSoStreamFalseCanOverrideGlobalTrue() {
        val config = CoreConfig().apply {
            isP2PDisabled = true
            mainStream = StreamConfig().apply { isP2PDisabled = false }
        }

        val js = CoreConfigJsMapper.toJsExpression(config)

        assertContains(js, "\"isP2PDisabled\":true")
        assertContains(js, "\"mainStream\":{\"isP2PDisabled\":false}")
    }

    @Test
    fun unassignedBooleansAreOmitted() {
        val js = CoreConfigJsMapper.toJsExpression(CoreConfig())

        assertFalse(js.contains("isP2PDisabled"), "unassigned booleans must not serialize")
        assertFalse(js.contains("isP2PUploadDisabled"), "unassigned booleans must not serialize")
    }

    @Test
    fun peerChurnAndWebRtcFieldsSerializeOnlyWhenSet() {
        val unset = CoreConfigJsMapper.toJsExpression(CoreConfig())
        assertFalse(unset.contains("p2pMaxPeers"))
        assertFalse(unset.contains("p2pChurnMaxPeersMultiplier"))
        assertFalse(unset.contains("webRtcOffersCount"))

        val set = CoreConfigJsMapper.toJsExpression(
            CoreConfig().apply {
                p2pMaxPeers = 40
                p2pChurnMaxPeersMultiplier = 2.0
                webRtcConnectionTimeoutMs = 10_000
            }
        )
        assertContains(set, "\"p2pMaxPeers\":40")
        assertContains(set, "\"p2pChurnMaxPeersMultiplier\":2.0")
        assertContains(set, "\"webRtcConnectionTimeoutMs\":10000")
    }

    @Test
    fun streamWithoutFunctionsEmitsNoGuard() {
        val config = CoreConfig().apply { mainStream = StreamConfig() }

        val js = CoreConfigJsMapper.toJsExpression(config)

        assertFalse(
            js.contains("config.mainStream = config.mainStream || {};"),
            "an empty stream scope must not emit a guard"
        )
    }
}
