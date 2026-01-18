package com.novage.p2pml.interop

fun interface OnReady {
    fun onReady()
}

fun interface OnError {
    fun onError(error: String)
}
