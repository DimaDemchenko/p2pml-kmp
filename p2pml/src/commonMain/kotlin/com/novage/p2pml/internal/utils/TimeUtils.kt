package com.novage.p2pml.internal.utils

import kotlin.time.TimeSource

internal expect fun getCurrentEpochSeconds(): Double

internal interface Clock {
    val timeSource: TimeSource
    fun getCurrentEpochSeconds(): Double
}

internal object SystemClock : Clock {
    override val timeSource: TimeSource get() = TimeSource.Monotonic
    override fun getCurrentEpochSeconds(): Double = com.novage.p2pml.internal.utils.getCurrentEpochSeconds()
}
