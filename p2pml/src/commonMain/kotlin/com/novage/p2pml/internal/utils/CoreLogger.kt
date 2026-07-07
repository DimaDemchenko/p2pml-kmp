package com.novage.p2pml.internal.utils

import com.novage.p2pml.api.logging.LogLevel
import com.novage.p2pml.api.logging.P2PLogging

internal class CoreLogger(className: String) {
    private val tag = "P2PML~$className"

    inline fun d(message: () -> String) = log(LogLevel.DEBUG, null, message)

    inline fun i(message: () -> String) = log(LogLevel.INFO, null, message)

    inline fun w(throwable: Throwable? = null, message: () -> String) = log(LogLevel.WARN, throwable, message)

    inline fun e(throwable: Throwable? = null, message: () -> String) = log(LogLevel.ERROR, throwable, message)

    inline fun log(level: LogLevel, throwable: Throwable?, message: () -> String) {
        val sink = P2PLogging.sink ?: return
        if (level >= P2PLogging.minLevel) sink.log(level, tag, message(), throwable)
    }
}

internal expect fun writeLog(level: LogLevel, tag: String, message: String, throwable: Throwable?)
