package com.novage.p2pml.api.interfaces

internal fun interface EventListener<T> {
    fun onEvent(data: T)
}
