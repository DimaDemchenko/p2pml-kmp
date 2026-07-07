package com.novage.p2pml.internal.utils

import android.util.Log
import com.novage.p2pml.api.logging.LogLevel

internal actual fun writeLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
    when (level) {
        LogLevel.DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
        LogLevel.INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
        LogLevel.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        LogLevel.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
