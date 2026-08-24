package com.novage.p2pml.internal.playback

import com.novage.p2pml.api.config.CoreConfig
import com.novage.p2pml.api.config.DynamicCoreConfig
import com.novage.p2pml.api.events.Segment
import com.novage.p2pml.api.playback.PlaybackInfo
import com.novage.p2pml.api.playback.PlaybackListener
import com.novage.p2pml.api.playback.PlaybackProvider
import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.parser.TimelineBounds
import com.novage.p2pml.internal.parser.hls.Stream
import com.novage.p2pml.internal.parser.hls.UpdateStreamParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException

private const val TOLERANCE = 1e-9
private const val VARIANT = "https://example.com/720p.m3u8"
private const val SEGMENT_DURATION = 4.0

/**
 * [SequenceStateTracker] owns two things the engine cannot do for itself: putting the reported
 * position on the parser's timeline, and telling the engine the playhead jumped.
 *
 * The second matters because the engine only ever writes `playback.position` from what it is handed —
 * it never re-derives it from a segment request — and AVPlayer reports the last *loaded* segment's
 * time after a seek. Without the forced position below, a seek would leave the engine parked at the
 * old playhead. These tests pin that machinery, including the two timing constants it depends on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SequenceStateTrackerTest {

    private class FakeProvider : PlaybackProvider {
        var listener: PlaybackListener? = null
            private set
        var setListenerCalls = 0
            private set

        override fun setPlaybackListener(listener: PlaybackListener?) {
            this.listener = listener
            setListenerCalls++
        }
    }

    private class FakeEngine : P2PEngine {
        val positions = mutableListOf<Pair<Double, Float>>()
        var throwOnUpdate = false

        override fun updatePlaybackInfo(positionSec: Double, speed: Float) {
            if (throwOnUpdate) throw SerializationException("NaN")
            positions += positionSec to speed
        }

        val lastPosition: Double? get() = positions.lastOrNull()?.first

        override suspend fun loadUrlAndWait(url: String) = Unit
        override suspend fun initCoreEngineAndWait(coreConfig: CoreConfig, uploadUrl: String) = Unit
        override fun destroy() = Unit
        override fun requestSegmentBytes(segmentUrl: String) = Unit
        override fun sendStream(stream: UpdateStreamParams) = Unit
        override fun sendAllStreams(streams: List<Stream>) = Unit
        override fun setManifestUrl(manifestUrl: String) = Unit
        override fun applyDynamicConfig(config: DynamicCoreConfig) = Unit
        override fun subscribeToP2PEvent(eventName: String) = Unit
        override fun unsubscribeFromP2PEvent(eventName: String) = Unit
    }

    private class FakeTimelineSource : PlaybackTimelineSource {
        var bounds: TimelineBounds? = TimelineBounds(start = 0.0, liveEdge = 100.0)
        val segments = mutableMapOf<String, Pair<String, Segment>>()

        override suspend fun getMainTimelineBounds(): TimelineBounds? = bounds

        override suspend fun getSegmentWithManifestByUrl(runtimeId: String): Pair<String, Segment>? =
            segments[runtimeId]

        fun register(runtimeId: String, externalId: Long, startTime: Double, variant: String = VARIANT) {
            segments[runtimeId] = variant to Segment(
                runtimeId = runtimeId,
                externalId = externalId,
                url = runtimeId,
                byteRange = null,
                startTime = startTime,
                endTime = startTime + SEGMENT_DURATION
            )
        }
    }

    private class Fixture(scope: TestScope) {
        val provider = FakeProvider()
        val engine = FakeEngine()
        val source = FakeTimelineSource()
        val tracker = SequenceStateTracker(
            playbackProvider = provider,
            p2pEngine = engine,
            timelineSource = source,
            dispatcher = StandardTestDispatcher(scope.testScheduler)
        )
    }

    /**
     * Runs a test against a fresh tracker and always tears it down: the tracker owns a collector on the
     * test scheduler, and leaving it running past the end of the test leaks a coroutine into teardown.
     */
    private fun withTracker(block: suspend TestScope.(Fixture) -> Unit) = runTest {
        val fixture = Fixture(this)
        try {
            block(fixture)
        } finally {
            fixture.tracker.destroy()
        }
    }

    /** Live report: the gap to the player's live edge is what carries over to the parser timeline. */
    private fun live(position: Double, liveEdge: Double, speed: Float = 1.0f) = PlaybackInfo(position, speed, liveEdge)

    @Test
    fun reportsThePositionConvertedOntoTheParserTimeline() = withTracker { f ->
        f.source.bounds = TimelineBounds(start = 0.0, liveEdge = 100.0)

        f.tracker.onPlaybackInfoUpdated(live(position = 27.0, liveEdge = 30.0))
        runCurrent()

        assertEquals(1, f.engine.positions.size)
        assertEquals(97.0, f.engine.lastPosition!!, TOLERANCE)
    }

    @Test
    fun skipsTheUpdateUntilTheParserHasTrackedSegments() = withTracker { f ->
        f.source.bounds = null

        f.tracker.onPlaybackInfoUpdated(live(position = 27.0, liveEdge = 30.0))
        runCurrent()

        // Sending a position before any segment exists would put it on an unrelated scale.
        assertTrue(f.engine.positions.isEmpty())
    }

    @Test
    fun forwardsPlaybackSpeedUnchanged() = withTracker { f ->

        f.tracker.onPlaybackInfoUpdated(live(position = 27.0, liveEdge = 30.0, speed = 2.5f))
        runCurrent()

        assertEquals(2.5f, f.engine.positions.single().second)
    }

    /**
     * The engine's playhead defaults to zero, but a PROGRAM-DATE-TIME playlist (AWS IVS, most
     * live origins) puts segments at epoch values. Without a cold-start sync the engine aborts
     * every initial load and live playback never starts — found on-device against IVS.
     */
    @Test
    fun firstSegmentRequestSyncsTheEnginePositionToTheStreamStart() = withTracker { f ->
        f.source.register("seg10", externalId = 10, startTime = 1_785_867_483.0)

        f.tracker.onSegmentRequested("seg10")
        runCurrent()

        assertEquals(1_785_867_483.0, assertNotNull(f.engine.lastPosition), TOLERANCE)
    }

    @Test
    fun sequentialSegmentRequestsAfterTheStartSyncAreNotSeeks() = withTracker { f ->
        f.source.register("seg10", externalId = 10, startTime = 40.0)
        f.source.register("seg11", externalId = 11, startTime = 44.0)
        f.source.register("seg12", externalId = 12, startTime = 48.0)

        f.tracker.onSegmentRequested("seg10")
        f.tracker.onSegmentRequested("seg11")
        f.tracker.onSegmentRequested("seg12")
        runCurrent()

        // Only the cold-start sync is sent; normal progression forces nothing further.
        assertEquals(1, f.engine.positions.size)
        assertEquals(40.0, assertNotNull(f.engine.lastPosition), TOLERANCE)
    }

    @Test
    fun repeatingTheSameSegmentRequestIsNotASeek() = withTracker { f ->
        f.source.register("seg10", externalId = 10, startTime = 40.0)

        f.tracker.onSegmentRequested("seg10")
        f.tracker.onSegmentRequested("seg10")
        runCurrent()

        assertEquals(1, f.engine.positions.size)
    }

    @Test
    fun aJumpInSegmentIdsForcesThePositionToThatSegmentStart() = withTracker { f ->
        f.source.register("seg10", externalId = 10, startTime = 40.0)
        f.source.register("seg50", externalId = 50, startTime = 200.0)

        f.tracker.onSegmentRequested("seg10")
        f.tracker.onSegmentRequested("seg50")
        runCurrent()

        // The engine learns the new playhead immediately, without waiting for the player to report it.
        assertEquals(200.0, assertNotNull(f.engine.lastPosition), TOLERANCE)
    }

    @Test
    fun variantSequencesAreTrackedIndependently() = withTracker { f ->
        val other = "https://example.com/480p.m3u8"
        f.source.register("a10", externalId = 10, startTime = 40.0)
        f.source.register("b11", externalId = 11, startTime = 44.0, variant = other)
        f.source.register("a11", externalId = 11, startTime = 44.0)

        f.tracker.onSegmentRequested("a10")
        // First request on a different variant re-syncs (an ABR switch lands near the playhead,
        // so the sync is a no-op for the engine's window) without disturbing the first variant.
        f.tracker.onSegmentRequested("b11")
        f.tracker.onSegmentRequested("a11")
        runCurrent()

        // Two start syncs (one per variant); a11 continues variant A's sequence and forces nothing.
        assertEquals(2, f.engine.positions.size)
    }

    @Test
    fun forcedPositionIsHeldWhileThePlayerIsStillReportingTheOldPlayhead() = withTracker { f ->
        f.source.register("seg10", externalId = 10, startTime = 40.0)
        f.source.register("seg50", externalId = 50, startTime = 200.0)
        f.source.bounds = TimelineBounds(start = 0.0, liveEdge = 300.0)

        f.tracker.onSegmentRequested("seg10")
        f.tracker.onSegmentRequested("seg50")
        runCurrent()
        f.engine.positions.clear()

        // Player still 250s behind the edge, i.e. position 50 — far from the 200 seek target.
        f.tracker.onPlaybackInfoUpdated(live(position = 50.0, liveEdge = 300.0))
        runCurrent()

        assertEquals(200.0, assertNotNull(f.engine.lastPosition), TOLERANCE)
    }

    /**
     * AVPlayer re-fetches from the start of its buffer window after returning from the background,
     * and that window sits behind the playhead. The request looks like a backwards seek, but the
     * player never comes back to it — measured on an iPhone 14, the engine was told 0.0 while
     * playback ran from 15s to 22s, for the full suspension, every time the app was foregrounded.
     * A gap that grows instead of shrinking is the signal that the inference was wrong.
     */
    @Test
    fun aTargetThePlayerIsMovingAwayFromIsAbandoned() = withTracker { f ->
        f.source.register("seg10", externalId = 10, startTime = 40.0)
        f.source.register("seg50", externalId = 50, startTime = 200.0)
        f.source.bounds = TimelineBounds(start = 0.0, liveEdge = 300.0)

        f.tracker.onSegmentRequested("seg50")
        f.tracker.onSegmentRequested("seg10")
        runCurrent()
        f.engine.positions.clear()

        // First tick has nothing to compare against, so the inferred target is still trusted.
        f.tracker.onPlaybackInfoUpdated(live(position = 260.0, liveEdge = 300.0))
        runCurrent()
        assertEquals(40.0, assertNotNull(f.engine.lastPosition), TOLERANCE)

        // Second tick: the player has moved further away, so the target is abandoned.
        f.tracker.onPlaybackInfoUpdated(live(position = 265.0, liveEdge = 300.0))
        runCurrent()
        assertEquals(265.0, assertNotNull(f.engine.lastPosition), TOLERANCE)

        // Standard tracking really has resumed — no residual pinning.
        f.tracker.onPlaybackInfoUpdated(live(position = 270.0, liveEdge = 300.0))
        runCurrent()
        assertEquals(270.0, assertNotNull(f.engine.lastPosition), TOLERANCE)
    }

    /** A real seek converges, so the target must be held for as long as the gap keeps shrinking. */
    @Test
    fun aTargetThePlayerIsConvergingOnIsHeldUntilCatchUp() = withTracker { f ->
        f.source.register("seg10", externalId = 10, startTime = 40.0)
        f.source.register("seg50", externalId = 50, startTime = 200.0)
        f.source.bounds = TimelineBounds(start = 0.0, liveEdge = 300.0)

        f.tracker.onSegmentRequested("seg10")
        f.tracker.onSegmentRequested("seg50")
        runCurrent()
        f.engine.positions.clear()

        // Gap 150, then 50: shrinking, so the 200.0 target stays in force.
        f.tracker.onPlaybackInfoUpdated(live(position = 50.0, liveEdge = 300.0))
        runCurrent()
        assertEquals(200.0, assertNotNull(f.engine.lastPosition), TOLERANCE)

        f.tracker.onPlaybackInfoUpdated(live(position = 150.0, liveEdge = 300.0))
        runCurrent()
        assertEquals(200.0, assertNotNull(f.engine.lastPosition), TOLERANCE)

        // Inside the threshold: catch-up fires and the player's own position is reported.
        f.tracker.onPlaybackInfoUpdated(live(position = 202.0, liveEdge = 300.0))
        runCurrent()
        assertEquals(202.0, assertNotNull(f.engine.lastPosition), TOLERANCE)
    }

    @Test
    fun playerCatchingUpToTheSeekTargetResumesStandardTracking() = withTracker { f ->
        f.source.register("seg10", externalId = 10, startTime = 40.0)
        f.source.register("seg50", externalId = 50, startTime = 200.0)
        f.source.bounds = TimelineBounds(start = 0.0, liveEdge = 300.0)

        f.tracker.onSegmentRequested("seg10")
        f.tracker.onSegmentRequested("seg50")
        runCurrent()
        f.engine.positions.clear()

        // Converted position 202.0 — inside the catch-up threshold around the 200 target. This is the
        // comparison that only works because both sides are now on the parser's timeline.
        f.tracker.onPlaybackInfoUpdated(live(position = 202.0, liveEdge = 300.0))
        runCurrent()
        assertEquals(202.0, assertNotNull(f.engine.lastPosition), TOLERANCE)

        // Tracking is standard again: a later, distant position is reported as-is rather than pinned.
        f.tracker.onPlaybackInfoUpdated(live(position = 250.0, liveEdge = 300.0))
        runCurrent()
        assertEquals(250.0, assertNotNull(f.engine.lastPosition), TOLERANCE)
    }

    @Test
    fun suspensionTimeoutResumesStandardTrackingWhenThePlayerNeverCatchesUp() = withTracker { f ->
        f.source.register("seg10", externalId = 10, startTime = 40.0)
        f.source.register("seg50", externalId = 50, startTime = 200.0)
        f.source.bounds = TimelineBounds(start = 0.0, liveEdge = 300.0)

        f.tracker.onSegmentRequested("seg10")
        f.tracker.onSegmentRequested("seg50")
        runCurrent()
        f.engine.positions.clear()

        advanceTimeBy(7_000)
        f.tracker.onPlaybackInfoUpdated(live(position = 50.0, liveEdge = 300.0))
        runCurrent()
        assertEquals(200.0, assertNotNull(f.engine.lastPosition), TOLERANCE, "still suspended before 8s")

        advanceTimeBy(2_000)
        runCurrent()
        f.tracker.onPlaybackInfoUpdated(live(position = 51.0, liveEdge = 300.0))
        runCurrent()

        // Backstop for a player that never converges: after the timeout its value is used again.
        assertEquals(51.0, assertNotNull(f.engine.lastPosition), TOLERANCE)
    }

    @Test
    fun aSecondSeekRestartsTheSuspensionWindow() = withTracker { f ->
        f.source.register("seg10", externalId = 10, startTime = 40.0)
        f.source.register("seg50", externalId = 50, startTime = 200.0)
        f.source.register("seg90", externalId = 90, startTime = 360.0)
        f.source.bounds = TimelineBounds(start = 0.0, liveEdge = 500.0)

        f.tracker.onSegmentRequested("seg10")
        f.tracker.onSegmentRequested("seg50")
        runCurrent()
        advanceTimeBy(7_000)

        f.tracker.onSegmentRequested("seg90")
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        f.engine.positions.clear()

        // 9s after the first seek but only 2s after the second, so the newer suspension still holds.
        f.tracker.onPlaybackInfoUpdated(live(position = 100.0, liveEdge = 500.0))
        runCurrent()

        assertEquals(360.0, assertNotNull(f.engine.lastPosition), TOLERANCE)
    }

    @Test
    fun forcedPositionCarriesTheLastKnownPlaybackSpeed() = withTracker { f ->
        f.source.register("seg10", externalId = 10, startTime = 40.0)
        f.source.register("seg50", externalId = 50, startTime = 200.0)

        f.tracker.onPlaybackInfoUpdated(live(position = 27.0, liveEdge = 30.0, speed = 1.75f))
        runCurrent()
        f.engine.positions.clear()

        f.tracker.onSegmentRequested("seg10")
        f.tracker.onSegmentRequested("seg50")
        runCurrent()

        // Both the start sync and the seek force carry the player's last reported speed.
        assertEquals(2, f.engine.positions.size)
        assertTrue(f.engine.positions.all { it.second == 1.75f })
    }

    @Test
    fun requestForAnUntrackedSegmentIsIgnored() = withTracker { f ->

        f.tracker.onSegmentRequested("never-seen")
        runCurrent()

        assertTrue(f.engine.positions.isEmpty())
    }

    @Test
    fun resetClearsSequenceHistoryAndAnyForcedPosition() = withTracker { f ->
        f.source.register("seg10", externalId = 10, startTime = 40.0)
        f.source.register("seg50", externalId = 50, startTime = 200.0)
        f.source.bounds = TimelineBounds(start = 0.0, liveEdge = 300.0)

        f.tracker.onSegmentRequested("seg10")
        f.tracker.onSegmentRequested("seg50")
        runCurrent()

        f.tracker.reset()
        f.engine.positions.clear()

        // Forced position is gone: the player's own value is reported again.
        f.tracker.onPlaybackInfoUpdated(live(position = 50.0, liveEdge = 300.0))
        runCurrent()
        assertEquals(50.0, assertNotNull(f.engine.lastPosition), TOLERANCE)

        // Sequence history is gone too: the same segment now counts as a stream start and
        // re-syncs the engine — exactly what the next stream needs after a switch.
        f.engine.positions.clear()
        f.tracker.onSegmentRequested("seg50")
        runCurrent()
        assertEquals(200.0, assertNotNull(f.engine.lastPosition), TOLERANCE)
    }

    @Test
    fun destroyUnregistersTheListener() = withTracker { f ->
        assertNotNull(f.provider.listener)

        f.tracker.destroy()

        assertNull(f.provider.listener)
    }

    @Test
    fun engineSerializationFailureDoesNotPropagate() = withTracker { f ->
        f.engine.throwOnUpdate = true

        f.tracker.onPlaybackInfoUpdated(live(position = 27.0, liveEdge = 30.0))
        runCurrent()

        // A non-finite position must not tear down the collector; later updates still arrive.
        f.engine.throwOnUpdate = false
        f.tracker.onPlaybackInfoUpdated(live(position = 28.0, liveEdge = 30.0))
        runCurrent()

        assertEquals(98.0, assertNotNull(f.engine.lastPosition), TOLERANCE)
    }
}
