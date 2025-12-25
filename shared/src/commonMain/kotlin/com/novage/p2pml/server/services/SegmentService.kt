package com.novage.p2pml.server.services

import com.novage.p2pml.engine.P2PEngine
import com.novage.p2pml.server.exceptions.SegmentReplacedException
import com.novage.p2pml.server.exceptions.TooManyRetriesException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SegmentService(private val p2pEngine: P2PEngine) {
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
                requests.remove(segmentUrl)
                throw TooManyRetriesException("Max retries exceeded")
            }

            previousState?.deferred?.completeExceptionally(
                SegmentReplacedException("Segment request replaced by newer one")
            )

            val newDeferred = CompletableDeferred<ByteArray>()
            requests[segmentUrl] = RequestState(newDeferred, currentAttempts + 1)

            p2pEngine.requestSegmentBytes(segmentUrl)

            return newDeferred
        }
    }

    suspend fun completeRequest(segmentUrl: String, segmentData: ByteArray) {
        mutex.withLock {
            val state = requests[segmentUrl] ?: return
            state.deferred.complete(segmentData)
            requests.remove(segmentUrl)
        }
    }

    suspend fun getPendingRequest(segmentUrl: String): CompletableDeferred<ByteArray>? =
        mutex.withLock { requests[segmentUrl]?.deferred }

    suspend fun removeRequest(segmentUrl: String) {
        mutex.withLock { requests.remove(segmentUrl) }
    }

    suspend fun cancelRequest(segmentUrl: String, deferredToCancel: CompletableDeferred<ByteArray>) {
        mutex.withLock {
            val state = requests[segmentUrl] ?: return

            if (state.deferred === deferredToCancel) {
                requests.remove(segmentUrl)
            }
        }
    }

    suspend fun reset() {
        mutex.withLock {
            val resetException = Exception("Engine Resetting")
            requests.values.forEach { it.deferred.completeExceptionally(resetException) }
            requests.clear()
        }
    }
}
