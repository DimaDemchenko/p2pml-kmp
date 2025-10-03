package com.novage.p2pml.server

import com.novage.p2pml.webview.WebViewManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SegmentHandler(private val webViewManager: WebViewManager) {
    private val mutex = Mutex()

    private val deferredSegments = mutableMapOf<String, CompletableDeferred<ByteArray>>()

    suspend fun registerSegmentRequest(segmentUrl: String): CompletableDeferred<ByteArray>? {
        mutex.withLock {
            if (segmentUrl in deferredSegments) return null

            val deferred = CompletableDeferred<ByteArray>()
            deferredSegments[segmentUrl] = deferred

            webViewManager.requestSegmentBytes(segmentUrl)

            return deferred
        }
    }

    suspend fun completeSegmentRequest(segmentUrl: String, segmentData: ByteArray) {
        mutex.withLock {
            deferredSegments[segmentUrl]?.complete(segmentData)
            deferredSegments.remove(segmentUrl)
        }
    }

    suspend fun getSegmentRequest(segmentUrl: String): CompletableDeferred<ByteArray>? =
        mutex.withLock { deferredSegments[segmentUrl] }

    suspend fun removeSegmentRequest(segmentUrl: String) {
        mutex.withLock { deferredSegments.remove(segmentUrl) }
    }

    suspend fun reset() {
        mutex.withLock {
            deferredSegments.forEach { (_, deferred) ->
                if (deferred.isCompleted) return@forEach

                deferred.completeExceptionally(
                    Exception("SegmentHandler is closing, no segment data will arrive.")
                )
            }
            deferredSegments.clear()
        }
    }
}
