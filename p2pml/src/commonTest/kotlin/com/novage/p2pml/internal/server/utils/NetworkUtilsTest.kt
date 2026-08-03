package com.novage.p2pml.internal.server.utils

import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkUtilsTest {

    private val wholeSegment = "https://example.com/seg0.ts"
    private val rangedSegment = "https://example.com/seg0.ts|500-999"

    @Test
    fun upstreamManifestUrlIsUntouchedWithoutDeliveryDirectives() {
        val signedUrl = "https://cdn.example.com/live/video.m3u8?token=a%2Fb&exp=123"

        assertEquals(signedUrl, buildUpstreamManifestUrl(signedUrl, Parameters.Empty))
    }

    @Test
    fun blockingReloadDirectivesAreAppended() {
        val params = parametersOf("_HLS_msn" to listOf("266"), "_HLS_part" to listOf("3"))

        assertEquals(
            "https://cdn.example.com/v.m3u8?_HLS_msn=266&_HLS_part=3",
            buildUpstreamManifestUrl("https://cdn.example.com/v.m3u8", params)
        )
    }

    @Test
    fun deliveryDirectivesMergeWithExistingQuery() {
        val params = parametersOf("_HLS_msn", "266")

        assertEquals(
            "https://cdn.example.com/v.m3u8?token=abc&_HLS_msn=266",
            buildUpstreamManifestUrl("https://cdn.example.com/v.m3u8?token=abc", params)
        )
    }

    @Test
    fun skipDirectiveIsNeverForwarded() {
        val params = parametersOf("_HLS_skip" to listOf("YES"), "_HLS_msn" to listOf("266"))

        assertEquals(
            "https://cdn.example.com/v.m3u8?_HLS_msn=266",
            buildUpstreamManifestUrl("https://cdn.example.com/v.m3u8", params)
        )
    }

    @Test
    fun nonDirectiveParametersAreNotForwarded() {
        val params = parametersOf("cacheBust" to listOf("42"), "_HLS_part" to listOf("3"))

        assertEquals(
            "https://cdn.example.com/v.m3u8?_HLS_part=3",
            buildUpstreamManifestUrl("https://cdn.example.com/v.m3u8", params)
        )
    }

    @Test
    fun directiveValuesAreUrlEncoded() {
        val params = parametersOf("_HLS_part", "a b&c")

        assertEquals(
            "https://cdn.example.com/v.m3u8?_HLS_part=a%20b%26c",
            buildUpstreamManifestUrl("https://cdn.example.com/v.m3u8", params)
        )
    }

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
    fun zeroContentLengthIsTreatedAsUnknownEnd() {
        assertTrue(payloadSatisfiesRequest("bytes=0-", wholeSegment, contentLength = 0))
        assertFalse(payloadSatisfiesRequest("bytes=0-0", wholeSegment, contentLength = 0))
    }

    @Test
    fun rangedSegmentRejectsMismatches() {
        assertFalse(payloadSatisfiesRequest(null, rangedSegment, contentLength = 500))
        assertFalse(payloadSatisfiesRequest("bytes=600-", rangedSegment, contentLength = 500))
        assertFalse(payloadSatisfiesRequest("bytes=500-800", rangedSegment, contentLength = 500))
        assertFalse(payloadSatisfiesRequest("bytes=0-", rangedSegment, contentLength = 500))
    }
}
