package com.novage.p2pml.internal.utils

import kotlin.coroutines.cancellation.CancellationException

/**
 * A safe version of Kotlin's standard [runCatching] that automatically re-throws
 * [CancellationException], preserving proper structured concurrency behavior
 * for Kotlin Coroutines.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (c: CancellationException) {
    throw c
} catch (e: Throwable) {
    Result.failure(e)
}
