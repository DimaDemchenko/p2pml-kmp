package com.novage.p2pml.interop

fun interface OnP2PReadyCallback {
    fun onReady()
}

fun interface OnP2PReadyErrorCallback {
    fun onError(error: String)
}
