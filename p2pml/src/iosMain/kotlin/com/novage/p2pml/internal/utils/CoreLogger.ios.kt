package com.novage.p2pml.internal.utils

import com.novage.p2pml.api.logging.LogLevel
import platform.Foundation.NSLog

internal actual fun writeLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
    val levelStr = when (level) {
        LogLevel.DEBUG -> "DEBUG"
        LogLevel.INFO -> "INFO"
        LogLevel.WARN -> "WARN"
        LogLevel.ERROR -> "ERROR"
    }
    val fullMessage = if (throwable != null) {
        "$message\n${throwable.stackTraceToString()}"
    } else {
        message
    }
    // Kotlin/Native does not bridge Kotlin String to NSString in variadic positions, so
    // NSLog format arguments crash. Escape '%' and pass a single pre-formatted string instead.
    NSLog("[$levelStr] [$tag] $fullMessage".replace("%", "%%"))
}
