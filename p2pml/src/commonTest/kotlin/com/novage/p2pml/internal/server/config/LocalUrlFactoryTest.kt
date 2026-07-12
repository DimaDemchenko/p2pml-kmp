package com.novage.p2pml.internal.server.config

import com.novage.p2pml.api.errors.P2PMediaLoaderErrorCode
import com.novage.p2pml.api.errors.P2PMediaLoaderException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LocalUrlFactoryTest {
    @Test
    fun generatesUniqueHexSessionTokens() {
        val first = LocalUrlFactory()
        val second = LocalUrlFactory()

        assertEquals(32, first.sessionToken.length)
        assertTrue(first.sessionToken.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(first.sessionToken, second.sessionToken)
    }

    @Test
    fun allUrlsCarryTheSessionToken() {
        val factory = LocalUrlFactory(sessionToken = "testtoken")
        factory.setPort(8080)

        val base = "http://127.0.0.1:8080/testtoken"
        assertEquals("$base/manifest/encoded", factory.buildManifestUrl("encoded"))
        assertEquals("$base/segment/encoded", factory.buildSegmentUrl("encoded"))
        assertTrue(factory.buildStaticPageUrl().startsWith("$base/"))
        assertTrue(factory.buildUploadUrl().startsWith("$base/"))
    }

    @Test
    fun throwsBeforePortIsAssigned() {
        val factory = LocalUrlFactory()

        val exception = assertFailsWith<P2PMediaLoaderException> {
            factory.buildManifestUrl("encoded")
        }
        assertEquals(P2PMediaLoaderErrorCode.NOT_INITIALIZED, exception.code)
    }
}
