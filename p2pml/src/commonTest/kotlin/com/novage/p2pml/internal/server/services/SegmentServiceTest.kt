package com.novage.p2pml.internal.server.services

import com.novage.p2pml.api.events.Segment
import com.novage.p2pml.api.playback.PlaybackListener
import com.novage.p2pml.api.playback.PlaybackProvider
import com.novage.p2pml.internal.parser.TimelineBounds
import com.novage.p2pml.internal.playback.PlaybackTimelineSource
import com.novage.p2pml.internal.playback.SequenceStateTracker
import com.novage.p2pml.internal.server.exceptions.SegmentAbortedException
import com.novage.p2pml.internal.server.exceptions.SegmentProcessingException
import com.novage.p2pml.internal.server.exceptions.SegmentReplacedException
import com.novage.p2pml.internal.server.exceptions.TooManyRetriesException
import io.ktor.utils.io.ByteChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Pins the pending-request state machine that coordinates three concurrent actors: the player's
 * GET handler (create/abandon), the engine's upload POST (complete/fail) and the session reset.
 * The fakes contain no suspension points, so every interleaving here is deterministic — the
 * assertions are exact, not "eventually consistent".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SegmentServiceTest {

    private val url = "https://cdn.example.com/seg1.ts"

    private class NoopPlaybackProvider : PlaybackProvider {
        override fun setPlaybackListener(listener: PlaybackListener?) = Unit
    }

    private class RecordingTimelineSource : PlaybackTimelineSource {
        val requestedRuntimeIds = mutableListOf<String>()

        override suspend fun getMainTimelineBounds(): TimelineBounds? = null

        override suspend fun getSegmentWithManifestByUrl(runtimeId: String): Pair<String, Segment>? {
            requestedRuntimeIds += runtimeId
            return null
        }
    }

    private class Harness(val engine: RecordingP2PEngine, val timelineSource: RecordingTimelineSource) {
        val tracker = SequenceStateTracker(NoopPlaybackProvider(), engine, timelineSource)
        val service = SegmentService(engine, tracker)
    }

    private fun serviceTest(block: suspend TestScope.(Harness) -> Unit) = runTest {
        val harness = Harness(RecordingP2PEngine(), RecordingTimelineSource())
        try {
            block(harness)
        } finally {
            harness.tracker.destroy()
        }
    }

    private fun payload() = ByteChannel(autoFlush = true).let { it to SegmentPayload(it, contentLength = null) }

    @Test
    fun firstRequestNotifiesTrackerFetchesOnceAndResolvesOnUpload() = serviceTest { h ->
        val deferred = h.service.createOrReplaceRequest(url)

        assertEquals(listOf(url), h.engine.requestedSegments)
        assertEquals(listOf(url), h.timelineSource.requestedRuntimeIds)

        val (_, segmentPayload) = payload()
        h.service.completeRequest(url, segmentPayload)

        assertEquals(segmentPayload, deferred.await())
    }

    @Test
    fun replacementFailsTheOldWaiterWithoutASecondFetch() = serviceTest { h ->
        val first = h.service.createOrReplaceRequest(url)
        val second = h.service.createOrReplaceRequest(url)

        assertEquals(1, h.engine.requestedSegments.size, "a replacement must not re-ask the engine")
        assertFailsWith<SegmentReplacedException> { first.await() }

        val (_, segmentPayload) = payload()
        h.service.completeRequest(url, segmentPayload)
        assertEquals(segmentPayload, second.await())
    }

    @Test
    fun retryCapFailsBothPartiesThenAllowsAFreshFetch() = serviceTest { h ->
        h.service.createOrReplaceRequest(url)
        h.service.createOrReplaceRequest(url)
        h.service.createOrReplaceRequest(url)
        val fourth = h.service.createOrReplaceRequest(url)

        // The fifth attempt hits MAX_RETRIES: the caller and the still-pending waiter both fail.
        assertFailsWith<TooManyRetriesException> { h.service.createOrReplaceRequest(url) }
        assertFailsWith<TooManyRetriesException> { fourth.await() }
        assertEquals(1, h.engine.requestedSegments.size)

        // The cap clears the entry, so the next request starts over with a real engine fetch.
        val fresh = h.service.createOrReplaceRequest(url)
        assertEquals(2, h.engine.requestedSegments.size)
        val (_, segmentPayload) = payload()
        h.service.completeRequest(url, segmentPayload)
        assertEquals(segmentPayload, fresh.await())
    }

    @Test
    fun completingAnUnknownSegmentDrainsItsChannel() = serviceTest { h ->
        val (channel, segmentPayload) = payload()

        h.service.completeRequest("unknown-segment", segmentPayload)

        assertTrue(channel.isClosedForWrite, "an unclaimed upload channel must be cancelled, or it hangs")
    }

    @Test
    fun abandonOnlyRemovesTheDeferredItWasGivenFor() = serviceTest { h ->
        val first = h.service.createOrReplaceRequest(url)
        val second = h.service.createOrReplaceRequest(url)

        // The first waiter's cleanup runs after it was already replaced; it must not evict the
        // replacement's registration.
        h.service.abandonRequest(url, first)

        val (_, segmentPayload) = payload()
        h.service.completeRequest(url, segmentPayload)
        assertEquals(segmentPayload, second.await())
    }

    @Test
    fun abandonDrainsAPayloadThatArrivedConcurrently() = serviceTest { h ->
        val deferred = h.service.createOrReplaceRequest(url)
        val (channel, segmentPayload) = payload()

        // The engine completes the request in the instant the waiter gives up (timeout path):
        // nobody will read this channel, so abandon must drain it.
        h.service.completeRequest(url, segmentPayload)
        h.service.abandonRequest(url, deferred)

        assertTrue(channel.isClosedForWrite)
    }

    @Test
    fun uploadsAfterAbandonAreTreatedAsUnknown() = serviceTest { h ->
        val deferred = h.service.createOrReplaceRequest(url)
        h.service.abandonRequest(url, deferred)

        val (channel, segmentPayload) = payload()
        h.service.completeRequest(url, segmentPayload)

        assertTrue(channel.isClosedForWrite)
        assertFalse(deferred.isCompleted, "an abandoned request must not receive late uploads")
    }

    @Test
    fun resetCancelsEveryPendingRequest() = serviceTest { h ->
        val first = h.service.createOrReplaceRequest("$url?a")
        val second = h.service.createOrReplaceRequest("$url?b")

        h.service.reset()

        assertTrue(first.isCancelled)
        assertTrue(second.isCancelled)

        // Uploads arriving after the reset belong to nobody and must be drained.
        val (channel, segmentPayload) = payload()
        h.service.completeRequest("$url?a", segmentPayload)
        assertTrue(channel.isClosedForWrite)
    }

    @Test
    fun failRequestRoutesAbortAndErrorDistinctly() = serviceTest { h ->
        val aborted = h.service.createOrReplaceRequest("$url?a")
        assertTrue(h.service.failRequest("$url?a", "aborted"))
        assertFailsWith<SegmentAbortedException> { aborted.await() }

        val errored = h.service.createOrReplaceRequest("$url?b")
        assertTrue(h.service.failRequest("$url?b", "http-error"))
        assertFailsWith<SegmentProcessingException> { errored.await() }

        assertFalse(h.service.failRequest("$url?unknown", "http-error"))
    }

    /**
     * 100 callers race the same segment. With MAX_RETRIES = 4 the state machine cycles in
     * periods of five calls: one engine fetch, three replacements, then the cap clears the
     * entry and fails both parties. The fakes never suspend, so the outcome is exact:
     * 20 fetches, 20 capped callers, 80 issued deferreds all failed (60 replaced + 20 capped),
     * and no pending state left behind.
     */
    @Test
    fun sustainedRetryStormSettlesWithNoPendingState() = serviceTest { h ->
        val issued = mutableListOf<kotlinx.coroutines.CompletableDeferred<SegmentPayload>>()
        var cappedCallers = 0

        repeat(100) {
            launch(UnconfinedTestDispatcher(testScheduler)) {
                try {
                    issued += h.service.createOrReplaceRequest(url)
                } catch (_: TooManyRetriesException) {
                    cappedCallers++
                }
            }
        }

        assertEquals(20, h.engine.requestedSegments.size)
        assertEquals(20, cappedCallers)
        assertEquals(80, issued.size)

        var replaced = 0
        var capped = 0
        issued.forEach { deferred ->
            when (runCatching { deferred.await() }.exceptionOrNull()) {
                is SegmentReplacedException -> replaced++
                is TooManyRetriesException -> capped++
                else -> error("every deferred must fail with a typed exception")
            }
        }
        assertEquals(60, replaced)
        assertEquals(20, capped)

        // The final cap cleared the map: a late upload is unknown and gets drained.
        val (channel, segmentPayload) = payload()
        h.service.completeRequest(url, segmentPayload)
        assertTrue(channel.isClosedForWrite)
    }
}
