package com.novage.p2pml.internal.interop

fun interface OnReady {
    fun onReady()
}

fun interface OnError {
    fun onError(error: String)
}
