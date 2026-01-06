package com.novage.p2pml.utils

import platform.Foundation.NSLog

internal actual fun writeLog(level: LogLevel, tag: String, message: String) {
    val levelStr = when (level) {
        LogLevel.DEBUG -> "DEBUG"
        LogLevel.INFO -> "INFO"
        LogLevel.WARN -> "WARN"
        LogLevel.ERROR -> "ERROR"
    }
    NSLog("[$levelStr] [$tag] $message")
}
