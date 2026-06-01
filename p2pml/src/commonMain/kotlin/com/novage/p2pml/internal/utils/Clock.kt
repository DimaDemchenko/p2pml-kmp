package com.novage.p2pml.internal.utils

import kotlin.time.TimeSource

internal interface Clock {
    val timeSource: TimeSource
    fun getCurrentEpochSeconds(): Double
}

private val epochSecondsProvider = ::getCurrentEpochSeconds

internal object SystemClock : Clock {
    override val timeSource: TimeSource get() = TimeSource.Monotonic
    override fun getCurrentEpochSeconds(): Double = epochSecondsProvider()
}
