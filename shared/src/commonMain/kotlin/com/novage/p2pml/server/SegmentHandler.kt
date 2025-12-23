package com.novage.p2pml.server

import com.novage.p2pml.engine.P2PEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


internal const val MAX_RETRIES = 4

class SegmentHandler(private val engineManager: P2PEngine) {
    private val mutex = Mutex()
    private data class RequestState(
        val deferred: CompletableDeferred<ByteArray>,
        val attemptCount: Int
    )

    private val requests = mutableMapOf<String, RequestState>()

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

            engineManager.requestSegmentBytes(segmentUrl)

            return newDeferred
        }
    }

    suspend fun completeSegmentRequest(segmentUrl: String, segmentData: ByteArray) {
        mutex.withLock {
            val state = requests[segmentUrl] ?: return
            state.deferred.complete(segmentData)
            requests.remove(segmentUrl)
        }
    }

    suspend fun getSegmentRequest(segmentUrl: String): CompletableDeferred<ByteArray>? =
        mutex.withLock { requests[segmentUrl]?.deferred }

    suspend fun removeSegmentRequest(segmentUrl: String) {
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
            requests.values.forEach {
                it.deferred.completeExceptionally(Exception("Resetting"))
            }
            requests.clear()
        }
    }
}
