package com.novage.p2pml.internal.playback

import com.novage.p2pml.api.playback.PlaybackInfo
import com.novage.p2pml.internal.parser.TimelineBounds
import kotlin.test.Test
import kotlin.test.assertEquals

private const val TOLERANCE = 1e-9

/**
 * The engine compares playback position against segment start times, so the only property that
 * matters is that a reported position lands on the parser's timeline and advances with playback.
 *
 * These tests pin that against the failure that motivated the mapper: a player whose position scale
 * slides (ExoPlayer measures position from the start of the live window, and that origin moves as
 * the playlist refreshes) used to freeze the position handed to the engine.
 */
class PlaybackTimelineMapperTest {

    private fun live(position: Double, liveEdge: Double) =
        PlaybackInfo(currentPlayPosition = position, currentPlaybackSpeed = 1.0f, currentLiveEdgePosition = liveEdge)

    private fun vod(position: Double) =
        PlaybackInfo(currentPlayPosition = position, currentPlaybackSpeed = 1.0f, currentLiveEdgePosition = null)

    @Test
    fun liveMapsPositionByItsDistanceBehindTheLiveEdge() {
        // 3s behind the player's live edge must land 3s behind the parser's live edge.
        val mapped = mapToStreamTime(live(position = 27.0, liveEdge = 30.0), TimelineBounds(0.0, 100.0))

        assertEquals(97.0, mapped, TOLERANCE)
    }

    @Test
    fun steadyLivePlaybackAdvancesOneSecondPerSecond() {
        // The player's numbers are frozen — position and window length both constant, because its
        // origin slides forward at the same rate the playhead does. Only the parser's edge advances.
        val mapped = (0..5).map { tick ->
            mapToStreamTime(live(position = 27.0, liveEdge = 30.0), TimelineBounds(0.0, 100.0 + tick))
        }

        assertEquals(97.0, mapped.first(), TOLERANCE)
        mapped.zipWithNext().forEach { (earlier, later) ->
            assertEquals(1.0, later - earlier, TOLERANCE)
        }
    }

    @Test
    fun livePositionTracksTheParserTimelineWhenThePlayerScaleSlides() {
        // Regression for the measured defect, using the numbers recorded on device: over 74s of
        // confirmed playback the player's window-relative position moved only +1.879s (44.021 ->
        // 45.900) while the parser's live edge correctly advanced +76.0s. The old code added a
        // once-latched anchor to that near-static value and reported +1.879s of progress.
        val playerLiveEdge = 60.0
        val timelineStart = 940.0

        val before = mapToStreamTime(
            live(position = 44.021, liveEdge = playerLiveEdge),
            TimelineBounds(timelineStart, liveEdge = 1_000.0)
        )
        val after = mapToStreamTime(
            live(position = 45.900, liveEdge = playerLiveEdge),
            TimelineBounds(timelineStart, liveEdge = 1_076.0)
        )

        assertEquals(984.021, before, TOLERANCE)
        assertEquals(77.879, after - before, TOLERANCE)
    }

    @Test
    fun pausedPlaybackHoldsPositionInsteadOfDriftingBackwards() {
        // Paused, the playhead is static while the window keeps sliding, so the player's
        // window-relative position shrinks by exactly what the parser's edge gains.
        val paused = PlaybackInfo(27.0, 0.0f, 30.0)
        val tenSecondsLater = PlaybackInfo(17.0, 0.0f, 30.0)

        val before = mapToStreamTime(paused, TimelineBounds(0.0, 100.0))
        val after = mapToStreamTime(tenSecondsLater, TimelineBounds(0.0, 110.0))

        assertEquals(before, after, TOLERANCE)
    }

    @Test
    fun vodPositionPassesThroughOnAZeroBasedTimeline() {
        val mapped = mapToStreamTime(vod(position = 42.5), TimelineBounds(0.0, 634.584))

        assertEquals(42.5, mapped, TOLERANCE)
    }

    @Test
    fun vodPositionIsOffsetWhenTheParserTimelineIsEpochBased() {
        // A VOD playlist carrying PROGRAM-DATE-TIME gets epoch segment times while the player still
        // reports 0-based positions, so a raw pass-through would land thousands of seconds away from
        // every segment and silently disable P2P.
        val epochStart = 1_785_058_000.0

        val mapped = mapToStreamTime(vod(position = 42.5), TimelineBounds(epochStart, epochStart + 600.0))

        assertEquals(epochStart + 42.5, mapped, TOLERANCE)
    }

    @Test
    fun positionAtOrBeyondTheLiveEdgeClampsToTheParserEdge() {
        // Player and parser read the same playlist a moment apart, so the player can appear slightly
        // ahead. Never report a position past the newest segment the engine knows about.
        val mapped = mapToStreamTime(live(position = 31.0, liveEdge = 30.0), TimelineBounds(0.0, 100.0))

        assertEquals(100.0, mapped, TOLERANCE)
    }

    @Test
    fun positionFurtherBackThanTheTrackedWindowClampsToTimelineStart() {
        // Seeking deep into a DVR window the parser has already evicted: the earliest known segment
        // is the best answer available.
        val mapped = mapToStreamTime(live(position = 0.0, liveEdge = 500.0), TimelineBounds(90.0, 100.0))

        assertEquals(90.0, mapped, TOLERANCE)
    }

    @Test
    fun speedIsCarriedIndependentlyOfTheTimelineConversion() {
        // Guards against a future refactor folding rate into the position maths.
        val fast = PlaybackInfo(27.0, 2.0f, 30.0)

        assertEquals(97.0, mapToStreamTime(fast, TimelineBounds(0.0, 100.0)), TOLERANCE)
    }
}
