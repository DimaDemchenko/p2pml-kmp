package com.novage.p2pml.utils

import android.util.Log

internal actual fun writeLog(level: LogLevel, tag: String, message: String) {
    when (level) {
        LogLevel.DEBUG -> Log.d(tag, message)
        LogLevel.INFO -> Log.i(tag, message)
        LogLevel.WARN -> Log.w(tag, message)
        LogLevel.ERROR -> Log.e(tag, message)
    }
}