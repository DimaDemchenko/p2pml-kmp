package com.novage.p2pml.api.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Lifecycle contract of the public event flows: they deliver while the core scope is alive,
 * complete normally once it is cancelled (the release() terminal signal), and keep driving the
 * first-collector-subscribes / last-collector-unsubscribes engine machinery.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class P2PEventsTest {

    private class Harness(testScope: TestScope) {
        val coreScope = CoroutineScope(UnconfinedTestDispatcher(testScope.testScheduler))
        val subscribed = mutableListOf<String>()
        val unsubscribed = mutableListOf<String>()

        // Mirrors production: the loader is never ACTIVE while P2PEvents is constructed, so the
        // watchers' initial zero-subscriber emission must not reach the engine callbacks.
        private var coreActive = false

        val events = P2PEvents(
            coreScope = coreScope,
            onSubscribe = { subscribed.add(it) },
            onUnsubscribe = { unsubscribed.add(it) },
            isCoreActive = { coreActive }
        )

        init {
            coreActive = true
        }
    }

    @Test
    fun streamsDeliverThenCompleteWhenCoreShutsDown() = runTest {
        val harness = Harness(this)
        val received = mutableListOf<ChunkDownloadedDetails>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            harness.events.onChunkDownloaded.collect { received.add(it) }
        }

        val details = ChunkDownloadedDetails(
            bytesLength = 42,
            downloadSource = DownloadSource.P2P,
            peerId = "peer-1",
            streamType = "main",
            infoHash = "aabbccdd"
        )
        harness.events.emitChunkDownloaded(details)

        assertEquals(listOf(details), received)
        assertFalse(collector.isCompleted)

        harness.coreScope.cancel()
        collector.join()

        assertTrue(collector.isCompleted)
        assertFalse(collector.isCancelled)
    }

    @Test
    fun collectorsStartedAfterShutdownCompleteImmediately() = runTest {
        val harness = Harness(this)
        harness.coreScope.cancel()

        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            harness.events.onPeerConnect.collect { fail("a released loader must not emit") }
        }
        collector.join()

        assertFalse(collector.isCancelled)
    }

    @Test
    fun engineSubscriptionFollowsFirstAndLastCollector() = runTest {
        val harness = Harness(this)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)

        val first = launch(dispatcher) { harness.events.onPeerConnect.collect {} }
        val second = launch(dispatcher) { harness.events.onPeerConnect.collect {} }
        assertEquals(listOf("onPeerConnect"), harness.subscribed)

        first.cancelAndJoin()
        assertTrue(harness.unsubscribed.isEmpty())

        second.cancelAndJoin()
        assertEquals(listOf("onPeerConnect"), harness.unsubscribed)

        harness.coreScope.cancel()
    }
}
