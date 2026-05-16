package com.novage.p2pml.internal.utils

private const val MILLISECONDS_IN_SECOND = 1000.0

internal actual fun getCurrentEpochSeconds(): Double = System.currentTimeMillis() / MILLISECONDS_IN_SECOND
