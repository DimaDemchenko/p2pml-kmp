package com.novage.p2pml.internal.parser.hlsPlaylistParser

import com.novage.p2pml.api.models.ByteRange
import com.novage.p2pml.internal.utils.Clock
import com.novage.p2pml.internal.parser.HlsStreamStateTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class HlsPlaylistParserTest {

    private val mockRewriter = object : HlsUrlRewriter {
        override fun rewriteVariantUrl(url: ParsedUrl, isIFrame: Boolean): String = "rewritten-var-" + url.original
        override fun rewriteRenditionUrl(url: ParsedUrl, type: String): String = "rewritten-rend-" + url.original
        override fun rewriteSessionKeyUrl(url: ParsedUrl): String = "rewritten-sess-" + url.original
        override fun rewriteSegmentUrl(url: ParsedUrl, byteRange: ByteRange?): String = "rewritten-seg-" + url.original
        override fun rewriteInitSegmentUrl(url: ParsedUrl): String = "rewritten-init-" + url.original
        override fun rewriteKeyUrl(url: ParsedUrl): String = "rewritten-key-" + url.original
        override fun rewriteLowLatencyUrl(url: ParsedUrl): String = "rewritten-ll-" + url.original
    }

    private class FakeTimeSource(var timeNs: Long = 0L) : TimeSource {
        inner class FakeTimeMark(val markNs: Long) : TimeMark {
            override fun elapsedNow() = (timeNs - markNs).nanoseconds
        }
        override fun markNow(): TimeMark = FakeTimeMark(timeNs)
    }

    @Test
    fun testResolveAbsoluteUrl() {
        // Absolute base, relative reference
        assertEquals(
            "http://example.com/path/segment.ts",
            resolveAbsoluteUrl("http://example.com/path/manifest.m3u8", "segment.ts")
        )

        // Absolute base, root-relative reference
        assertEquals(
            "http://example.com/segment.ts",
            resolveAbsoluteUrl("http://example.com/path/manifest.m3u8", "/segment.ts")
        )

        // Absolute base, parent-relative reference
        assertEquals(
            "http://example.com/path/segment.ts",
            resolveAbsoluteUrl("http://example.com/path/sub/manifest.m3u8", "../segment.ts")
        )

        // Already absolute reference
        assertEquals(
            "http://other.com/segment.ts",
            resolveAbsoluteUrl("http://example.com/path/manifest.m3u8", "http://other.com/segment.ts")
        )
    }

    @Test
    fun testHeaderValidation() {
        val parser = HlsPlaylistParser(urlRewriter = mockRewriter)
        
        // Correct header
        val correctPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            segment.ts
        """.trimIndent()
        
        // This should pass without error (fails with if invalid)
        parser.parse("http://example.com/manifest.m3u8", correctPlaylist)

        // Correct header with BOM
        val correctPlaylistWithBOM = "\uFEFF#EXTM3U\n" +
            "#EXT-X-VERSION:3\n" +
            "#EXT-X-TARGETDURATION:10\n" +
            "#EXTINF:10.0,\n" +
            "segment.ts"
        parser.parse("http://example.com/manifest.m3u8", correctPlaylistWithBOM)

        // Missing header
        val missingHeader = """
            #EXT-X-VERSION:3
        """.trimIndent()
        
        assertFailsWith<IllegalArgumentException> {
            parser.parse("http://example.com/manifest.m3u8", missingHeader)
        }

        // Invalid prefix header
        val invalidPrefixHeader = """
            #EXTM3U-something
            #EXT-X-VERSION:3
        """.trimIndent()
        
        assertFailsWith<IllegalArgumentException> {
            parser.parse("http://example.com/manifest.m3u8", invalidPrefixHeader)
        }
    }

    @Test
    fun testParseMultivariantPlaylist() {
        val manifest = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-DEFINE:NAME="QUALITY",VALUE="high"
            #EXT-X-SESSION-KEY:METHOD=AES-128,URI="https://priv.key"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-group",NAME="English",URI="audio_en.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=1280x720,CODECS="avc1.640028,mp4a.40.2",AUDIO="audio-group"
            video_{${'$'}QUALITY}.m3u8
            #EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=860000,URI="iframe.m3u8"
        """.trimIndent()

        val parser = HlsPlaylistParser(urlRewriter = mockRewriter)
        val result = parser.parse("http://example.com/master.m3u8", manifest)

        val playlist = result.playlist as HlsMultivariantPlaylist
        assertEquals("http://example.com/master.m3u8", playlist.baseUri)

        // Check session keys
        assertEquals(1, playlist.sessionKeyUrls.size)
        assertEquals("https://priv.key", playlist.sessionKeyUrls[0].original)
        assertEquals("https://priv.key", playlist.sessionKeyUrls[0].absolute)

        // Check variants
        assertEquals(2, playlist.variants.size)
        val mainVariant = playlist.variants.first { !it.isIFrame }
        assertEquals("http://example.com/video_high.m3u8", mainVariant.url.absolute)
        assertEquals(1280000, mainVariant.bandwidth)
        assertEquals(1280, mainVariant.width)
        assertEquals(720, mainVariant.height)
        assertEquals("avc1.640028,mp4a.40.2", mainVariant.codecs)
        assertEquals("audio-group", mainVariant.audioGroupId)

        val iframeVariant = playlist.variants.first { it.isIFrame }
        assertEquals("http://example.com/iframe.m3u8", iframeVariant.url.absolute)
        assertEquals(860000, iframeVariant.bandwidth)

        // Check rewritten output
        assertTrue(result.rewrittenManifest.contains("URI=\"rewritten-sess-https://priv.key\""))
        assertTrue(result.rewrittenManifest.contains("URI=\"rewritten-rend-audio_en.m3u8\""))
        assertTrue(result.rewrittenManifest.contains("rewritten-var-video_{\$QUALITY}.m3u8"))
        assertTrue(result.rewrittenManifest.contains("URI=\"rewritten-var-iframe.m3u8\""))
    }

    @Test
    fun testParseMediaPlaylist() {
        val manifest = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:100
            #EXT-X-KEY:METHOD=AES-128,URI="https://key"
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:9.009,
            segment1.ts
            #EXT-X-BYTERANGE:1024@2048
            #EXTINF:8.5,
            segment2.ts
            #EXT-X-PART:DURATION=2.0,URI="part1.ts"
            #EXT-X-PRELOAD-HINT:TYPE=PART,URI="preload.ts"
            #EXT-X-RENDITION-REPORT:URI="report.m3u8",LAST-MSN=101
            #EXT-X-ENDLIST
        """.trimIndent()

        val parser = HlsPlaylistParser(urlRewriter = mockRewriter)
        val result = parser.parse("http://example.com/media.m3u8", manifest)

        val playlist = result.playlist as HlsMediaPlaylist
        assertEquals("http://example.com/media.m3u8", playlist.baseUri)
        assertEquals(100, playlist.mediaSequence)
        assertTrue(playlist.hasEndTag)

        // Check segments
        assertEquals(2, playlist.hlsSegments.size)
        val seg1 = playlist.hlsSegments[0]
        assertEquals("http://example.com/segment1.ts", seg1.url.absolute)
        assertEquals(9009000L, seg1.durationUs)
        assertEquals(0L, seg1.byteRangeOffset)
        assertEquals(-1L, seg1.byteRangeLength)
        assertNull(seg1.byteRange)
        assertEquals("https://key", seg1.encryptionKey?.original)
        assertEquals("init.mp4", seg1.initializationSegment?.url?.original)

        val seg2 = playlist.hlsSegments[1]
        assertEquals("http://example.com/segment2.ts", seg2.url.absolute)
        assertEquals(8500000L, seg2.durationUs)
        assertEquals(2048L, seg2.byteRangeOffset)
        assertEquals(1024L, seg2.byteRangeLength)
        assertEquals(ByteRange(2048, 3071), seg2.byteRange)

        // Check LL-HLS lists
        assertEquals(1, playlist.parts.size)
        assertEquals("part1.ts", playlist.parts[0].original)
        assertEquals(1, playlist.preloadHints.size)
        assertEquals("preload.ts", playlist.preloadHints[0].original)
        assertEquals(1, playlist.renditionReports.size)
        assertEquals("report.m3u8", playlist.renditionReports[0].original)

        // Check rewritten output
        assertTrue(result.rewrittenManifest.contains("URI=\"rewritten-key-https://key\""))
        assertTrue(result.rewrittenManifest.contains("URI=\"rewritten-init-init.mp4\""))
        assertTrue(result.rewrittenManifest.contains("rewritten-seg-segment1.ts"))
        assertTrue(result.rewrittenManifest.contains("rewritten-seg-segment2.ts"))
        assertTrue(result.rewrittenManifest.contains("URI=\"rewritten-ll-part1.ts\""))
        assertTrue(result.rewrittenManifest.contains("URI=\"rewritten-ll-preload.ts\""))
        assertTrue(result.rewrittenManifest.contains("URI=\"rewritten-ll-report.m3u8\""))
    }

    @Test
    fun testStreamStateTrackerMocking() {
        val fakeTimeSource = FakeTimeSource()
        var fakeEpoch = 1000.0

        val fakeClock = object : Clock {
            override val timeSource: TimeSource get() = fakeTimeSource
            override fun getCurrentEpochSeconds(): Double = fakeEpoch
        }

        val tracker = HlsStreamStateTracker(clock = fakeClock)

        // Setup some parsed segments
        val segments = listOf(
            HlsSegment(
                url = ParsedUrl("seg1.ts", "http://example.com/seg1.ts"),
                byteRangeOffset = 0,
                byteRangeLength = -1,
                durationUs = 10_000_000, // 10s
                programDateTimeUs = null,
                initializationSegment = null,
                encryptionKey = null
            ),
            HlsSegment(
                url = ParsedUrl("seg2.ts", "http://example.com/seg2.ts"),
                byteRangeOffset = 0,
                byteRangeLength = -1,
                durationUs = 10_000_000, // 10s
                programDateTimeUs = null,
                initializationSegment = null,
                encryptionKey = null
            )
        )

        val livePlaylist = HlsMediaPlaylist(
            baseUri = "http://example.com/live.m3u8",
            mediaSequence = 1,
            hasEndTag = false, // Live stream!
            hlsSegments = segments,
            parts = emptyList(),
            preloadHints = emptyList(),
            renditionReports = emptyList()
        )

        // Process live media playlist
        tracker.postProcessMediaPlaylist("http://example.com/live.m3u8", livePlaylist)

        // Verify live variant is tracked
        assertTrue(tracker.isManifestTracked("http://example.com/live.m3u8"))

        // Eviction test: Move fake time forward by 30 seconds (TTL is 60s)
        fakeTimeSource.timeNs += 30.seconds.inWholeNanoseconds
        
        val livePlaylist2 = HlsMediaPlaylist(
            baseUri = "http://example.com/live-other.m3u8",
            mediaSequence = 1,
            hasEndTag = false,
            hlsSegments = segments,
            parts = emptyList(),
            preloadHints = emptyList(),
            renditionReports = emptyList()
        )
        
        // Process a different live playlist, should not evict yet
        tracker.postProcessMediaPlaylist("http://example.com/live-other.m3u8", livePlaylist2)
        assertTrue(tracker.isManifestTracked("http://example.com/live.m3u8"))

        // Move time past 60s TTL (ttl is 60s, we add another 40s -> total 70s since first update)
        fakeTimeSource.timeNs += 40.seconds.inWholeNanoseconds
        
        // Processing live-other again should now evict the first one
        tracker.postProcessMediaPlaylist("http://example.com/live-other.m3u8", livePlaylist2)
        assertFalse(tracker.isManifestTracked("http://example.com/live.m3u8"))
    }
}
