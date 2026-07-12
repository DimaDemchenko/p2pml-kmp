package com.novage.p2pml.internal.parser

import com.novage.p2pml.api.events.ByteRange
import kotlin.test.Test
import kotlin.test.assertEquals

class SegmentUtilsTest {

    @Test
    fun runtimeIdWithoutByteRangeIsPlainUrl() {
        val url = "https://example.com/seg0.ts"
        assertEquals(url, buildSegmentRuntimeId(url, byteRange = null))
    }

    @Test
    fun runtimeIdEncodesByteRange() {
        val url = "https://example.com/seg0.ts"
        assertEquals("$url|100-199", buildSegmentRuntimeId(url, ByteRange(100, 199)))
    }

    @Test
    fun decodeRecoversUrlFromByteRangeRuntimeId() {
        val url = "https://example.com/seg0.ts"
        val runtimeId = buildSegmentRuntimeId(url, ByteRange(100, 199))
        assertEquals(url, segmentUrlFromRuntimeId(runtimeId))
    }

    @Test
    fun decodeLeavesPlainUrlUnchanged() {
        val url = "https://example.com/seg0.ts"
        assertEquals(url, segmentUrlFromRuntimeId(url))
    }
}
