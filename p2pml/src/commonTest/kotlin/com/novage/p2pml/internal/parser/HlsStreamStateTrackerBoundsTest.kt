package com.novage.p2pml.internal.parser

import com.novage.p2pml.internal.parser.hls.HlsMediaPlaylist
import com.novage.p2pml.internal.parser.hls.HlsMultivariantPlaylist
import com.novage.p2pml.internal.parser.hls.HlsSegment
import com.novage.p2pml.internal.parser.hls.ParsedUrl
import com.novage.p2pml.internal.parser.hls.Rendition
import com.novage.p2pml.internal.parser.hls.Variant
import com.novage.p2pml.internal.utils.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.TimeSource

private const val TOLERANCE = 1e-6
private const val SEGMENT_DURATION_US = 4_000_000L
private const val FIRST_PDT_US = 1_785_058_100_000_000L
private const val FIRST_PDT_SEC = 1_785_058_100.0

/**
 * [HlsStreamStateTracker.getMainTimelineBounds] is the reference the reported playback position is
 * mapped onto, so these tests pin what "the timeline" means: which streams count, and how the bounds
 * move as a live playlist slides.
 */
class HlsStreamStateTrackerBoundsTest {

    private val fixedEpoch = 1_785_058_000.0

    private val fixedClock = object : Clock {
        override val timeSource: TimeSource get() = TimeSource.Monotonic
        override fun getCurrentEpochSeconds(): Double = fixedEpoch
    }

    private fun tracker() = HlsStreamStateTracker(clock = fixedClock)

    private fun segment(name: String, programDateTimeUs: Long? = null) = HlsSegment(
        url = ParsedUrl(name, "http://example.com/$name"),
        byteRangeOffset = 0,
        byteRangeLength = -1,
        durationUs = SEGMENT_DURATION_US,
        programDateTimeUs = programDateTimeUs
    )

    private fun playlist(url: String, segments: List<HlsSegment>, mediaSequence: Long = 0, isLive: Boolean = true) =
        HlsMediaPlaylist(
            baseUri = url,
            mediaSequence = mediaSequence,
            hasEndTag = !isLive,
            hlsSegments = segments
        )

    @Test
    fun boundsAreNullBeforeAnySegmentIsTracked() {
        assertNull(tracker().getMainTimelineBounds())
    }

    @Test
    fun boundsSpanTheTrackedVodSegments() {
        val tracker = tracker()
        val url = "http://example.com/vod.m3u8"

        tracker.postProcessMediaPlaylist(url, playlist(url, listOf(segment("a.ts"), segment("b.ts")), isLive = false))

        val bounds = assertNotNull(tracker.getMainTimelineBounds())
        assertEquals(0.0, bounds.start, TOLERANCE)
        assertEquals(8.0, bounds.liveEdge, TOLERANCE)
    }

    @Test
    fun boundsAreEpochBasedWhenThePlaylistCarriesProgramDateTime() {
        val tracker = tracker()
        val url = "http://example.com/pdt.m3u8"

        tracker.postProcessMediaPlaylist(
            url,
            playlist(
                url,
                listOf(
                    segment("a.ts", programDateTimeUs = FIRST_PDT_US),
                    segment("b.ts", programDateTimeUs = FIRST_PDT_US + SEGMENT_DURATION_US)
                )
            )
        )

        val bounds = assertNotNull(tracker.getMainTimelineBounds())
        assertEquals(FIRST_PDT_SEC, bounds.start, TOLERANCE)
        assertEquals(FIRST_PDT_SEC + 8.0, bounds.liveEdge, TOLERANCE)
    }

    @Test
    fun liveEdgeAdvancesAsThePlaylistSlides() {
        val tracker = tracker()
        val url = "http://example.com/live.m3u8"

        val firstRefresh = playlist(url, listOf(segment("a.ts"), segment("b.ts")), mediaSequence = 0)
        tracker.postProcessMediaPlaylist(url, firstRefresh)
        val firstEdge = assertNotNull(tracker.getMainTimelineBounds()).liveEdge

        // Next refresh drops the oldest segment and appends a new one, as a live playlist does.
        val secondRefresh = playlist(url, listOf(segment("b.ts"), segment("c.ts")), mediaSequence = 1)
        tracker.postProcessMediaPlaylist(url, secondRefresh)
        val secondEdge = assertNotNull(tracker.getMainTimelineBounds()).liveEdge

        assertEquals(SEGMENT_DURATION_US / 1_000_000.0, secondEdge - firstEdge, TOLERANCE)
    }

    /** Segments anchored by PROGRAM-DATE-TIME, so expected bounds are exact rather than clock-seeded. */
    private fun pdtSegments(count: Int, prefix: String, startUs: Long = FIRST_PDT_US) = (0 until count).map { index ->
        segment("$prefix$index.ts", programDateTimeUs = startUs + index * SEGMENT_DURATION_US)
    }

    @Test
    fun liveEdgeFollowsTheFreshestVariantAfterAQualitySwitch() {
        val tracker = tracker()
        val stale = "http://example.com/480p.m3u8"
        val fresh = "http://example.com/720p.m3u8"

        tracker.postProcessMediaPlaylist(stale, playlist(stale, pdtSegments(count = 1, prefix = "s")))
        tracker.postProcessMediaPlaylist(fresh, playlist(fresh, pdtSegments(count = 3, prefix = "f")))

        // A variant the player stopped refreshing must not hold the edge back.
        val bounds = assertNotNull(tracker.getMainTimelineBounds())
        assertEquals(FIRST_PDT_SEC + 12.0, bounds.liveEdge, TOLERANCE)
    }

    @Test
    fun audioRenditionsDoNotContributeToTheMainTimeline() {
        val tracker = tracker()
        val master = "http://example.com/master.m3u8"
        val video = "http://example.com/video.m3u8"
        val audio = "http://example.com/audio.m3u8"

        tracker.postProcessMultivariantPlaylist(
            master,
            HlsMultivariantPlaylist(
                baseUri = master,
                variants = listOf(Variant(url = ParsedUrl("video.m3u8", video), bandwidth = 1_000)),
                videos = emptyList(),
                audios = listOf(Rendition(url = ParsedUrl("audio.m3u8", audio), groupId = "aac", name = "English"))
            )
        )

        tracker.postProcessMediaPlaylist(video, playlist(video, pdtSegments(count = 1, prefix = "v")))
        // Audio is segmented differently and runs further ahead; it must not define the main edge.
        tracker.postProcessMediaPlaylist(audio, playlist(audio, pdtSegments(count = 3, prefix = "a")))

        val bounds = assertNotNull(tracker.getMainTimelineBounds())
        assertEquals(FIRST_PDT_SEC + 4.0, bounds.liveEdge, TOLERANCE)
    }

    @Test
    fun liveStreamEndingKeepsItsTimelineAndAddsNoDuplicates() {
        val tracker = tracker()
        val url = "http://example.com/live.m3u8"

        tracker.postProcessMediaPlaylist(
            url,
            playlist(url, listOf(segment("a.ts"), segment("b.ts")), mediaSequence = 0)
        )
        tracker.postProcessMediaPlaylist(
            url,
            playlist(url, listOf(segment("b.ts"), segment("c.ts")), mediaSequence = 1)
        )
        val boundsWhileLive = assertNotNull(tracker.getMainTimelineBounds())

        // The event ends: the same playlist arrives once more with #EXT-X-ENDLIST appended.
        tracker.postProcessMediaPlaylist(
            url,
            playlist(url, listOf(segment("b.ts"), segment("c.ts")), mediaSequence = 1, isLive = false)
        )

        val update = assertNotNull(tracker.getUpdateStreamParams(url))
        assertEquals(emptyList(), update.addSegments, "final window must not re-register as new segments")

        val trackedB = assertNotNull(tracker.getSegmentWithManifestByUrl("http://example.com/b.ts"))
        assertEquals(1L, trackedB.second.externalId, "ids must stay stable across the live-to-VOD flip")

        val bounds = assertNotNull(tracker.getMainTimelineBounds())
        assertEquals(boundsWhileLive.start, bounds.start, TOLERANCE)
        assertEquals(boundsWhileLive.liveEdge, bounds.liveEdge, TOLERANCE)
    }

    @Test
    fun boundsAreClearedOnReset() {
        val tracker = tracker()
        val url = "http://example.com/live.m3u8"

        tracker.postProcessMediaPlaylist(url, playlist(url, listOf(segment("a.ts"))))
        assertNotNull(tracker.getMainTimelineBounds())

        tracker.reset()

        assertNull(tracker.getMainTimelineBounds())
    }
}
