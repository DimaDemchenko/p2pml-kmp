package com.novage.p2pml.api.interop

import com.novage.p2pml.P2PMediaLoaderErrorType

fun interface OnReady {
    fun onReady()
}

fun interface OnError {
    fun onError(errorType: P2PMediaLoaderErrorType, message: String)
}
