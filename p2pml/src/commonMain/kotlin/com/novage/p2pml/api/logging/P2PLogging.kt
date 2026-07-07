package com.novage.p2pml.api.logging

import com.novage.p2pml.internal.utils.writeLog
import kotlin.concurrent.Volatile

/**
 * Global logging configuration for the library.
 *
 * By default, [LogLevel.WARN] and above are written to the platform log (Logcat / NSLog).
 * Lower [minLevel] to [LogLevel.DEBUG] before initializing the loader for full diagnostics,
 * or replace [sink] to route messages elsewhere. Setting [sink] to null silences the
 * library entirely.
 *
 * Note: debug-level messages include manifest and segment URLs, which may carry signed
 * query parameters.
 */
object P2PLogging {
    /** Destination for log messages. Defaults to the platform log; null silences all output. */
    @Volatile
    var sink: P2PLogger? = P2PLogger(::writeLog)

    /** Lowest severity forwarded to [sink]. Defaults to [LogLevel.WARN]. */
    @Volatile
    var minLevel: LogLevel = LogLevel.WARN
}
