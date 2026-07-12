package com.novage.p2pml.internal.server.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkUtilsTest {

    private val wholeSegment = "https://example.com/seg0.ts"
    private val rangedSegment = "https://example.com/seg0.ts|500-999"

    @Test
    fun parsesOpenEndedRange() {
        assertEquals(RequestedByteRange(100, null), parseSingleByteRange("bytes=100-"))
    }

    @Test
    fun parsesBoundedRange() {
        assertEquals(RequestedByteRange(0, 499), parseSingleByteRange("bytes=0-499"))
    }

    @Test
    fun rejectsMalformedAndMultiRanges() {
        assertNull(parseSingleByteRange("bytes=abc"))
        assertNull(parseSingleByteRange("bytes=-500"))
        assertNull(parseSingleByteRange("bytes=0-1,5-9"))
        assertNull(parseSingleByteRange("items=0-1"))
        assertNull(parseSingleByteRange("bytes=200-100"))
    }

    @Test
    fun wholeSegmentMatchesWholeFileRequests() {
        assertTrue(payloadSatisfiesRequest(null, wholeSegment, contentLength = 1000))
        assertTrue(payloadSatisfiesRequest("bytes=0-", wholeSegment, contentLength = 1000))
        assertTrue(payloadSatisfiesRequest("bytes=0-999", wholeSegment, contentLength = 1000))
        assertTrue(payloadSatisfiesRequest("bytes=0-", wholeSegment, contentLength = null))
    }

    @Test
    fun wholeSegmentRejectsMidSegmentResume() {
        assertFalse(payloadSatisfiesRequest("bytes=100-", wholeSegment, contentLength = 1000))
    }

    @Test
    fun wholeSegmentRejectsPrefixRequest() {
        assertFalse(payloadSatisfiesRequest("bytes=0-499", wholeSegment, contentLength = 1000))
        assertFalse(payloadSatisfiesRequest("bytes=0-499", wholeSegment, contentLength = null))
    }

    @Test
    fun unparseableRangeFallsBackToWholeFileSemantics() {
        assertTrue(payloadSatisfiesRequest("bytes=abc", wholeSegment, contentLength = 1000))
        assertFalse(payloadSatisfiesRequest("bytes=abc", rangedSegment, contentLength = 500))
    }

    @Test
    fun rangedSegmentMatchesItsExactSpan() {
        assertTrue(payloadSatisfiesRequest("bytes=500-999", rangedSegment, contentLength = 500))
        assertTrue(payloadSatisfiesRequest("bytes=500-", rangedSegment, contentLength = 500))
        assertTrue(payloadSatisfiesRequest("bytes=500-999", rangedSegment, contentLength = null))
    }

    @Test
    fun rangedSegmentRejectsMismatches() {
        assertFalse(payloadSatisfiesRequest(null, rangedSegment, contentLength = 500))
        assertFalse(payloadSatisfiesRequest("bytes=600-", rangedSegment, contentLength = 500))
        assertFalse(payloadSatisfiesRequest("bytes=500-800", rangedSegment, contentLength = 500))
        assertFalse(payloadSatisfiesRequest("bytes=0-", rangedSegment, contentLength = 500))
    }
}
