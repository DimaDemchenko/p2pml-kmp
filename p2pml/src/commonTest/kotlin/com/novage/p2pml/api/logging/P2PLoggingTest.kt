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
    fun enableLoggingLowersLevelToDebug() {
        P2PLogging.disableLogging()
        assertFalse(P2PLogging.isDebugEnabled)

        P2PLogging.enableLogging()

        assertEquals(LogLevel.DEBUG, P2PLogging.minLevel)
        assertTrue(P2PLogging.isDebugEnabled)
    }

    @Test
    fun disableLoggingRestoresWarn() {
        P2PLogging.enableLogging()

        P2PLogging.disableLogging()

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
