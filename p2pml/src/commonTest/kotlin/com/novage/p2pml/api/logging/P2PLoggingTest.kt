package com.novage.p2pml.api.logging

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class P2PLoggingTest {
    private val originalMinLevel = P2PLogging.minLevel

    @AfterTest
    fun tearDown() {
        P2PLogging.minLevel = originalMinLevel
    }

    @Test
    fun enableDebugLoggingLowersLevelToDebug() {
        P2PLogging.disableDebugLogging()
        assertFalse(P2PLogging.isDebugEnabled)

        P2PLogging.enableDebugLogging()

        assertEquals(LogLevel.DEBUG, P2PLogging.minLevel)
        assertTrue(P2PLogging.isDebugEnabled)
    }

    @Test
    fun disableDebugLoggingRestoresWarn() {
        P2PLogging.enableDebugLogging()

        P2PLogging.disableDebugLogging()

        assertEquals(LogLevel.WARN, P2PLogging.minLevel)
        assertFalse(P2PLogging.isDebugEnabled)
    }

    @Test
    fun isDebugEnabledIsTrueOnlyAtDebugLevel() {
        for (level in LogLevel.entries) {
            P2PLogging.minLevel = level
            assertEquals(level == LogLevel.DEBUG, P2PLogging.isDebugEnabled, "level=$level")
        }
    }
}
