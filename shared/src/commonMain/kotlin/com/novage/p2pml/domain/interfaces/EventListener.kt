package com.novage.p2pml.domain.interfaces

fun interface EventListener<T> {
    fun onEvent(data: T)
}
