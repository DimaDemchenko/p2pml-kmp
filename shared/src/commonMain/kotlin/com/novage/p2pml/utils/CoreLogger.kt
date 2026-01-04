package com.novage.p2pml.utils

internal object LogConfig {
    var isEnabled: Boolean = false
}

internal enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

internal class CoreLogger(private val className: String) {
    private val tag = "P2PML~$className"

    inline fun d(message: () -> String) {
        if (LogConfig.isEnabled) writeLog(LogLevel.DEBUG, tag, message())
    }

    inline fun i(message: () -> String) {
        if (LogConfig.isEnabled) writeLog(LogLevel.INFO, tag, message())
    }

    inline fun w(message: () -> String) {
        if (LogConfig.isEnabled) writeLog(LogLevel.WARN, tag, message())
    }

    inline fun e(throwable: Throwable? = null, message: () -> String) {
        if (LogConfig.isEnabled) {
            val msg = if (throwable != null) {
                "${message()}\nStacktrace: ${throwable.stackTraceToString()}"
            } else {
                message()
            }
            writeLog(LogLevel.ERROR, tag, msg)
        }
    }
}

internal expect fun writeLog(level: LogLevel, tag: String, message: String)