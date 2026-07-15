package com.novage.p2pml.internal.server.services

import com.novage.p2pml.internal.engine.P2PEngine
import com.novage.p2pml.internal.playback.SequenceStateTracker
import com.novage.p2pml.internal.server.exceptions.SegmentAbortedException
import com.novage.p2pml.internal.server.exceptions.SegmentProcessingException
import com.novage.p2pml.internal.server.exceptions.SegmentReplacedException
import com.novage.p2pml.internal.server.exceptions.TooManyRetriesException
import com.novage.p2pml.internal.utils.CoreLogger
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class SegmentPayload(val channel: ByteReadChannel, val contentLength: Long?)

internal class SegmentService(
    private val p2pEngine: P2PEngine,
    private val sequenceStateTracker: SequenceStateTracker
) {
    private val logger = CoreLogger("SegmentService")

    private val mutex = Mutex()
    private val requests = mutableMapOf<String, RequestState>()

    private data class RequestState(val deferred: CompletableDeferred<SegmentPayload>, val attemptCount: Int)

    companion object {
        private const val MAX_RETRIES = 4
        private const val ENGINE_ABORT_ERROR_TYPE = "aborted"
    }

    suspend fun createOrReplaceRequest(segmentUrl: String): CompletableDeferred<SegmentPayload> {
        val newDeferred = CompletableDeferred<SegmentPayload>()
        var isFirstRequest = false
        var oldDeferred: CompletableDeferred<SegmentPayload>? = null
        var oldException: Exception? = null

        val errorToThrow = mutex.withLock {
            val previousState = requests[segmentUrl]
            val currentAttempts = previousState?.attemptCount ?: 0

            if (currentAttempts >= MAX_RETRIES) {
                logger.w { "Max retries ($MAX_RETRIES) exceeded for segment: $segmentUrl" }
                requests.remove(segmentUrl)
                oldDeferred = previousState?.deferred
                oldException = TooManyRetriesException("Max retries exceeded for segment: $segmentUrl")
                return@withLock oldException
            }

            if (previousState != null) {
                logger.d { "Re-queueing pending download (Attempt ${currentAttempts + 1}) for: $segmentUrl" }
                oldDeferred = previousState.deferred
                oldException = SegmentReplacedException("Segment request replaced by newer one")
            } else {
                logger.d { "Registered pending download for: $segmentUrl" }
                isFirstRequest = true
            }

            requests[segmentUrl] = RequestState(newDeferred, currentAttempts + 1)
            null
        }

        if (oldDeferred != null && oldException != null) {
            oldDeferred.completeExceptionally(oldException)
        }

        if (errorToThrow != null) {
            throw errorToThrow
        }

        if (isFirstRequest) {
            sequenceStateTracker.onSegmentRequested(segmentUrl)
            p2pEngine.requestSegmentBytes(segmentUrl)
        }

        return newDeferred
    }

    suspend fun completeRequest(segmentUrl: String, payload: SegmentPayload) {
        val state = mutex.withLock {
            requests.remove(segmentUrl)
        }

        if (state == null) {
            logger.d { "Received data for unknown/cancelled segment: $segmentUrl. Ignoring." }
            payload.channel.cancel(null)
            return
        }

        logger.d { "Segment stream received. Resolving pending download for: $segmentUrl" }
        state.deferred.complete(payload)
    }

    suspend fun failRequest(segmentUrl: String, errorMsg: String): Boolean {
        val state = mutex.withLock { requests.remove(segmentUrl) }

        if (state == null) {
            logger.w { "Received error for unknown segment ID: $segmentUrl" }
            return false
        }

        if (errorMsg == ENGINE_ABORT_ERROR_TYPE) {
            logger.i { "Segment upload aborted: $segmentUrl" }
            state.deferred.completeExceptionally(SegmentAbortedException("Segment aborted - $segmentUrl"))
        } else {
            logger.w { "Error processing segment: $segmentUrl - $errorMsg" }
            state.deferred.completeExceptionally(
                SegmentProcessingException("Error processing segment - $segmentUrl - $errorMsg")
            )
        }
        return true
    }

    /**
     * Detaches a caller that no longer waits on [deferredToAbandon] (timeout, error, disconnect).
     * Removes the map entry if it still belongs to this deferred. If the engine completed the
     * deferred concurrently (or completes it before anyone notices), the payload channel is
     * drained here — nobody will read it anymore, and an unconsumed channel suspends the
     * upload route forever once its buffer fills.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun abandonRequest(segmentUrl: String, deferredToAbandon: CompletableDeferred<SegmentPayload>) {
        mutex.withLock {
            if (requests[segmentUrl]?.deferred === deferredToAbandon) {
                logger.d { "Abandoning active pending download: $segmentUrl" }
                requests.remove(segmentUrl)
            }
        }

        deferredToAbandon.invokeOnCompletion { cause ->
            if (cause == null) {
                logger.d { "Draining orphaned segment payload for: $segmentUrl" }
                deferredToAbandon.getCompleted().channel.cancel(null)
            }
        }
    }

    suspend fun reset() {
        val deferredsToCancel = mutex.withLock {
            if (requests.isNotEmpty()) {
                logger.i { "Resetting service. Cancelling ${requests.size} pending downloads." }
            }
            val copy = requests.values.map { it.deferred }
            requests.clear()
            copy
        }

        deferredsToCancel.forEach { it.cancel() }
    }
}
