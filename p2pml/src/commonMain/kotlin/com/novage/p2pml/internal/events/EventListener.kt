package com.novage.p2pml.internal.events

internal fun interface EventListener<T> {
    fun onEvent(data: T)
}
