package com.novage.p2pml.internal.webview

import com.novage.p2pml.api.events.P2PEvents
import com.novage.p2pml.api.logging.LogLevel
import com.novage.p2pml.api.logging.P2PLogger
import com.novage.p2pml.api.logging.P2PLogging
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class WebViewMessageRouterTest {
    private val originalSink = P2PLogging.sink
    private val originalMinLevel = P2PLogging.minLevel

    private val engineLogs = mutableListOf<Pair<LogLevel, String>>()

    @BeforeTest
    fun setUp() {
        P2PLogging.minLevel = LogLevel.DEBUG
        P2PLogging.sink = P2PLogger { level, tag, message, _ ->
            if (tag == "P2PML~WebEngine") engineLogs.add(level to message)
        }
    }

    @AfterTest
    fun tearDown() {
        P2PLogging.sink = originalSink
        P2PLogging.minLevel = originalMinLevel
    }

    private fun createRouter(onCoreInitResult: ((String?) -> Unit)? = null) = WebViewMessageRouter(
        events = P2PEvents(
            coreScope = CoroutineScope(Dispatchers.Unconfined),
            onSubscribe = {},
            onUnsubscribe = {},
            isCoreActive = { true }
        ),
        onCoreInitResult = onCoreInitResult
    )

    @Test
    fun forwardsEngineLogEntriesAtMappedLevels() {
        createRouter().handleMessage(
            """
            {"type":"onEngineLog","payload":{"entries":[
                {"level":"debug","text":"d"},
                {"level":"log","text":"l"},
                {"level":"info","text":"i"},
                {"level":"warn","text":"w"},
                {"level":"error","text":"e"}
            ]}}
            """.trimIndent()
        )

        assertEquals(
            listOf(
                LogLevel.DEBUG to "d",
                LogLevel.DEBUG to "l",
                LogLevel.INFO to "i",
                LogLevel.WARN to "w",
                LogLevel.ERROR to "e"
            ),
            engineLogs
        )
    }

    @Test
    fun engineLogEntriesRespectMinLevel() {
        P2PLogging.minLevel = LogLevel.WARN

        createRouter().handleMessage(
            """{"type":"onEngineLog","payload":
                {"entries":[{"level":"debug","text":"d"},{"level":"error","text":"e"}]}}"""
        )

        assertEquals(listOf(LogLevel.ERROR to "e"), engineLogs)
    }

    @Test
    fun malformedEngineLogPayloadDoesNotThrow() {
        createRouter().handleMessage("""{"type":"onEngineLog","payload":{"entries":"bogus"}}""")

        assertTrue(engineLogs.isEmpty())
    }

    @Test
    fun coreInitSuccessInvokesCallbackWithNull() {
        val results = mutableListOf<String?>()

        createRouter { results.add(it) }
            .handleMessage("""{"type":"onCoreInitialized","payload":{}}""")

        assertEquals(listOf<String?>(null), results)
    }

    @Test
    fun coreInitFailureInvokesCallbackWithMessage() {
        val results = mutableListOf<String?>()

        createRouter { results.add(it) }
            .handleMessage(
                """{"type":"onCoreInitFailed","payload":{"message":"Core is not a constructor","stack":"..."}}"""
            )

        assertEquals(listOf<String?>("Core is not a constructor"), results)
    }

    @Test
    fun coreInitFailureWithMalformedPayloadFallsBackToGenericMessage() {
        val results = mutableListOf<String?>()

        createRouter { results.add(it) }
            .handleMessage("""{"type":"onCoreInitFailed","payload":"bogus"}""")

        assertEquals(listOf<String?>("Unknown JS core initialization error"), results)
    }

    @Test
    fun coreInitFailureWithoutPayloadFallsBackToGenericMessage() {
        val results = mutableListOf<String?>()

        createRouter { results.add(it) }
            .handleMessage("""{"type":"onCoreInitFailed"}""")

        assertEquals(listOf<String?>("Unknown JS core initialization error"), results)
    }
}
