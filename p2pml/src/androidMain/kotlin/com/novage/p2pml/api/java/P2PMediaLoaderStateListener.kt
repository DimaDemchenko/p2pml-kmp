package com.novage.p2pml.api.java

import com.novage.p2pml.api.state.P2PMediaLoaderState

/**
 * Java-friendly callback for observing [P2PMediaLoaderState] changes.
 *
 * Registered via [P2PMediaLoaderJava.addStateListener]. Callbacks are invoked on a background
 * thread (`Dispatchers.Default`); switch to the main thread before touching UI.
 */
fun interface P2PMediaLoaderStateListener {
    fun onStateChanged(state: P2PMediaLoaderState)
}
