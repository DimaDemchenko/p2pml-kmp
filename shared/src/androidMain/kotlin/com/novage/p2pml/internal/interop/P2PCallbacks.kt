package com.novage.p2pml.internal.interop

import com.novage.p2pml.MediaLoaderErrorType

fun interface OnReady {
    fun onReady()
}

fun interface OnError {
    fun onError(errorType: MediaLoaderErrorType, message: String)
}
