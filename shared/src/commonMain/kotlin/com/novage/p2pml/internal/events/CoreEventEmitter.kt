package com.novage.p2pml.internal.events

import com.novage.p2pml.internal.events.EventListener
import com.novage.p2pml.api.models.CoreEventMap

internal interface CoreEventEmitter {
    fun <T> addEventListener(event: CoreEventMap<T>, listener: EventListener<T>)
    fun <T> removeEventListener(event: CoreEventMap<T>, listener: EventListener<T>)
    fun <T> emit(event: CoreEventMap<T>, data: T)
    fun removeAllListeners()
    fun getSubscribedEventNames(): List<String>
    fun <T> hasListeners(event: CoreEventMap<T>): Boolean
}
