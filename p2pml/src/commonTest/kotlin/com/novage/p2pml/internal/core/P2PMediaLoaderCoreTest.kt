package com.novage.p2pml.internal.core

import com.novage.p2pml.api.errors.P2PMediaLoaderErrorCode
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import com.novage.p2pml.api.logging.P2PLogger
import com.novage.p2pml.api.logging.P2PLogging
import com.novage.p2pml.api.state.P2PMediaLoaderStatus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class P2PMediaLoaderCoreTest {
    private var originalSink: P2PLogger? = null

    @BeforeTest
    fun setUp() {
        originalSink = P2PLogging.sink
        P2PLogging.sink = null
    }

    @AfterTest
    fun tearDown() {
        P2PLogging.sink = originalSink
    }

    @Test
    fun releaseBeforeInitializeLatchesReleased() {
        val core = P2PMediaLoaderCore()

        core.release()

        assertEquals(P2PMediaLoaderStatus.RELEASED, core.state.value.status)
        assertNull(core.state.value.error)
    }

    @Test
    fun releaseBeforeInitializeIsTerminal() {
        val core = P2PMediaLoaderCore()

        core.release()
        core.release(
            P2PMediaLoaderException(P2PMediaLoaderErrorCode.ENGINE_CRASHED, "late failure")
        )

        assertEquals(P2PMediaLoaderStatus.RELEASED, core.state.value.status)
        assertNull(core.state.value.error)
    }

    @Test
    fun failureBeforeInitializeLatchesFailedWithCause() {
        val core = P2PMediaLoaderCore()
        val failure = P2PMediaLoaderException(P2PMediaLoaderErrorCode.ENGINE_CRASHED, "boom")

        core.release(failure)

        assertEquals(P2PMediaLoaderStatus.FAILED, core.state.value.status)
        assertEquals(failure, core.state.value.error)
    }
}
