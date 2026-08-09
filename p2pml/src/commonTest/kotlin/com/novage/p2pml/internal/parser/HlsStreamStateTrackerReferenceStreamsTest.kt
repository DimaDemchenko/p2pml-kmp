package com.novage.p2pml.internal.parser

import com.novage.p2pml.api.events.StreamType
import com.novage.p2pml.internal.parser.hls.HlsMediaPlaylist
import com.novage.p2pml.internal.parser.hls.HlsMultivariantPlaylist
import com.novage.p2pml.internal.parser.hls.HlsPlaylistParser
import com.novage.p2pml.internal.parser.hls.ReferenceStreamManifests
import com.novage.p2pml.internal.server.config.LocalUrlFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Feeds the parsed [ReferenceStreamManifests] through [HlsStreamStateTracker] and pins the
 * layer the parser tests cannot see: stream registration, media-sequence-based segment ids,
 * timeline chaining and runtime-id lookup. Expected values are derived from the manifest text.
 */
class HlsStreamStateTrackerReferenceStreamsTest {

    private val parser = HlsPlaylistParser(
        urlRewriter = LocalHlsUrlRewriter(LocalUrlFactory(sessionToken = "tok").apply { setPort(8080) })
    )

    private val hevcBase = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc"
    private val hevcMasterUrl = "$hevcBase/master.m3u8"
    private val hevcMediaUrl = "$hevcBase/v5/prog_index.m3u8"
    private val muxMediaUrl = "https://test-streams.mux.dev/x36xhzz/url_0/193039199_mp4_h264_aac_hd_7.m3u8"

    private fun hevcTrackerWithMaster(): HlsStreamStateTracker {
        val tracker = HlsStreamStateTracker()
        val master = parser.parse(hevcMasterUrl, ReferenceStreamManifests.APPLE_HEVC_MASTER).playlist
            as HlsMultivariantPlaylist
        tracker.postProcessMultivariantPlaylist(hevcMasterUrl, master)
        return tracker
    }

    private fun HlsStreamStateTracker.processHevcMedia() {
        val media = parser.parse(hevcMediaUrl, ReferenceStreamManifests.APPLE_HEVC_MEDIA).playlist
            as HlsMediaPlaylist
        postProcessMediaPlaylist(hevcMediaUrl, media)
    }

    @Test
    fun hevcMasterRegistersDeclaredStreams() {
        val tracker = hevcTrackerWithMaster()

        // 18 unique non-i-frame variant URIs become main streams; the three audio renditions
        // become secondary streams. Subtitles and i-frame playlists are not P2P streams.
        val streams = tracker.getStreams()
        assertEquals(21, streams.size)
        assertEquals(18, streams.count { it.type == StreamType.MAIN })
        assertEquals(3, streams.count { it.type == StreamType.SECONDARY })

        assertTrue(tracker.isManifestTracked(hevcMasterUrl))
        assertTrue(tracker.isManifestTracked(hevcMediaUrl))
        assertTrue(tracker.isManifestTracked("$hevcBase/a1/prog_index.m3u8"))
        assertFalse(tracker.isManifestTracked("$hevcBase/tp5/iframe_index.m3u8"))
        assertFalse(tracker.isManifestTracked("$hevcBase/s1/en/prog_index.m3u8"))
    }

    @Test
    fun hevcByteRangeLadderGetsMediaSequenceBasedIds() {
        val tracker = hevcTrackerWithMaster()
        tracker.processHevcMedia()

        // EXT-X-MEDIA-SEQUENCE:1 numbers the 76 byte-range slices 1..76; a VOD refresh removes
        // nothing below the sequence start.
        val update = assertNotNull(tracker.getUpdateStreamParams(hevcMediaUrl))
        assertFalse(update.isLive)
        assertTrue(update.removeSegmentsIds.isEmpty())
        assertEquals(76, update.addSegments.size)
        assertEquals((1L..76L).toList(), update.addSegments.map { it.externalId })

        // The timeline chains gaplessly from zero: each slice starts where the previous ended.
        val segments = update.addSegments
        assertEquals(0.0, segments.first().startTime)
        segments.zipWithNext().forEach { (previous, next) ->
            assertEquals(previous.endTime, next.startTime)
        }

        // Byte-range runtime ids resolve to their segment and owning playlist.
        val runtimeId = "$hevcBase/v5/main.mp4|1118-1701211"
        val (manifestUrl, segment) = assertNotNull(tracker.getSegmentWithManifestByUrl(runtimeId))
        assertEquals(hevcMediaUrl, manifestUrl)
        assertEquals(1L, segment.externalId)
        assertEquals("$hevcBase/v5/main.mp4", segment.url)

        // Only the byte-range-qualified ids are current segments — the bare URL is not one.
        assertTrue(tracker.isCurrentSegment(runtimeId))
        assertFalse(tracker.isCurrentSegment("$hevcBase/v5/main.mp4"))
    }

    @Test
    fun hevcTimelineBoundsSpanTheVodDuration() {
        val tracker = hevcTrackerWithMaster()
        tracker.processHevcMedia()

        // The 76 EXTINF durations sum to exactly 600.0 seconds.
        val bounds = assertNotNull(tracker.getMainTimelineBounds())
        assertEquals(0.0, bounds.start)
        assertEquals(600.0, bounds.liveEdge, 0.001)
    }

    @Test
    fun muxMediaWithoutMediaSequenceTagStartsIdsAtZero() {
        val tracker = HlsStreamStateTracker()
        val media = parser.parse(muxMediaUrl, ReferenceStreamManifests.MUX_MEDIA).playlist
            as HlsMediaPlaylist

        // Played ad hoc, without a master: the stream registers itself as a main stream.
        tracker.postProcessMediaPlaylist(muxMediaUrl, media)

        val update = assertNotNull(tracker.getUpdateStreamParams(muxMediaUrl))
        assertEquals((0L..63L).toList(), update.addSegments.map { it.externalId })

        // The 64 EXTINF durations sum to 634.584 seconds.
        val bounds = assertNotNull(tracker.getMainTimelineBounds())
        assertEquals(0.0, bounds.start)
        assertEquals(634.584, bounds.liveEdge, 0.001)
    }
}
