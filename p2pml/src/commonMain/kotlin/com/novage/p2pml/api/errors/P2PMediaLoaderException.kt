package com.novage.p2pml.api.errors

/**
 * Stable reason codes for [P2PMediaLoaderException].
 *
 * Every observable exception is fatal: it is either thrown from `initialize` (and reflected as a
 * `FAILED` loader state) or surfaced as a `FAILED` state after the core self-releases at runtime.
 * Branch on [P2PMediaLoaderErrorCode] for analytics or messaging.
 */
enum class P2PMediaLoaderErrorCode {
    /** The internal proxy server failed to bind/start. */
    SERVER_START_FAILED,

    /** The engine page (WebView) did not signal ready within the startup timeout. */
    ENGINE_LOAD_TIMEOUT,

    /** The engine page failed to load (HTTP/resource error during startup). */
    ENGINE_LOAD_FAILED,

    /** A generic startup/system failure while bringing the engine up. */
    ENGINE_INIT_FAILED,

    /** The engine (WebView) crashed or its bridge broke at runtime. The core self-releases. */
    ENGINE_CRASHED,

    /** The loader was used before initialization completed or after release. */
    NOT_INITIALIZED
}

/**
 * Exception describing a fatal P2P Media Loader startup or runtime failure.
 *
 * Surfaced either by throwing from `initialize`/`createPlaybackUrl`, or as the cause attached to a
 * `FAILED` loader state (see [com.novage.p2pml.api.state.P2PMediaLoaderState]). When this occurs, P2P
 * acceleration cannot continue and the host should fall back to the origin URL.
 *
 * @property code stable reason code; use it for analytics/branching.
 * @property message human-readable description (always present).
 */
class P2PMediaLoaderException(
    val code: P2PMediaLoaderErrorCode,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)
