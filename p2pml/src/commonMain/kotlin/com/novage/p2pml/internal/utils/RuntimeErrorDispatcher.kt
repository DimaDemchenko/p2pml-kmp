package com.novage.p2pml.internal.utils

import com.novage.p2pml.P2PMediaLoaderErrorType
import com.novage.p2pml.P2PMediaLoaderException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class RuntimeErrorDispatcher {
    private val mutableErrors = MutableSharedFlow<P2PMediaLoaderException>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errors = mutableErrors.asSharedFlow()

    fun tryEmit(type: P2PMediaLoaderErrorType, message: String) {
        mutableErrors.tryEmit(P2PMediaLoaderException(type, message))
    }
}
