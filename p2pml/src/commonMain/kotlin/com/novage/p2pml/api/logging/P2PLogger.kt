package com.novage.p2pml.api.logging

/**
 * Receives log messages emitted by the library.
 *
 * Install a custom implementation via [P2PLogging.sink] to route messages into the host
 * app's logging pipeline (e.g. Timber, Crashlytics, os_log). The default sink writes to
 * Logcat on Android and NSLog on iOS.
 */
fun interface P2PLogger {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}
