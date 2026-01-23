package com.novage.p2pml.domain.interfaces

internal fun interface EventListener<T> {
    fun onEvent(data: T)
}
