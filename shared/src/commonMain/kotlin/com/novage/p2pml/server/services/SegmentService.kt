package com.novage.p2pml.server.services

import com.novage.p2pml.domain.interfaces.P2PEngine
import com.novage.p2pml.server.exceptions.SegmentReplacedException
import com.novage.p2pml.server.exceptions.TooManyRetriesException
import com.novage.p2pml.utils.CoreLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SegmentService(private val p2pEngine: P2PEngine) {
    private val logger = CoreLogger("SegmentService")

    private val mutex = Mutex()
    private val requests = mutableMapOf<String, RequestState>()

    private data class RequestState(
        val deferred: CompletableDeferred<ByteArray>,
        val attemptCount: Int
    )

    companion object {
        private const val MAX_RETRIES = 4
    }

    suspend fun createOrReplaceRequest(segmentUrl: String): CompletableDeferred<ByteArray> {
        mutex.withLock {
            val previousState = requests[segmentUrl]
            val currentAttempts = previousState?.attemptCount ?: 0

            if (currentAttempts >= MAX_RETRIES) {
                logger.w { "Max retries ($MAX_RETRIES) exceeded for segment: $segmentUrl" }
                requests.remove(segmentUrl)
                throw TooManyRetriesException("Max retries exceeded")
            }

            if (previousState != null) {
                logger.d { "Re-queueing pending download (Attempt ${currentAttempts + 1}) for: $segmentUrl" }
                previousState.deferred.completeExceptionally(
                    SegmentReplacedException("Segment request replaced by newer one")
                )
            } else {
                logger.d { "Registered pending download for: $segmentUrl" }
            }

            val newDeferred = CompletableDeferred<ByteArray>()
            requests[segmentUrl] = RequestState(newDeferred, currentAttempts + 1)

            p2pEngine.requestSegmentBytes(segmentUrl)

            return newDeferred
        }
    }

    suspend fun completeRequest(segmentUrl: String, segmentData: ByteArray) {
        mutex.withLock {
            val state = requests[segmentUrl]

            if (state == null) {
                logger.d { "Received data for unknown/cancelled segment: $segmentUrl. Ignoring." }
                return
            }

            logger.d { "Segment bytes received. Resolving pending download for: $segmentUrl" }
            state.deferred.complete(segmentData)
            requests.remove(segmentUrl)
        }
    }

    suspend fun getPendingRequest(segmentUrl: String): CompletableDeferred<ByteArray>? =
        mutex.withLock { requests[segmentUrl]?.deferred }

    suspend fun removeRequest(segmentUrl: String) {
        mutex.withLock {
            if (requests.containsKey(segmentUrl)) {
                logger.d { "Removing pending download state: $segmentUrl" }
                requests.remove(segmentUrl)
            }
        }
    }

    suspend fun cancelRequest(segmentUrl: String, deferredToCancel: CompletableDeferred<ByteArray>) {
        mutex.withLock {
            val state = requests[segmentUrl] ?: return

            if (state.deferred === deferredToCancel) {
                logger.d { "Cancelling active pending download: $segmentUrl" }
                requests.remove(segmentUrl)
            }
        }
    }

    suspend fun reset() {
        mutex.withLock {
            val count = requests.size
            if (count > 0) {
                logger.i { "Resetting service. Cancelling $count pending downloads." }
            }

            val resetException = Exception("Engine Resetting")
            requests.values.forEach { it.deferred.completeExceptionally(resetException) }
            requests.clear()
        }
    }
}