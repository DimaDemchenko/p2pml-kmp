package com.novage.p2pml.internal.events

import com.novage.p2pml.api.interfaces.CoreEventEmitter
import com.novage.p2pml.api.interfaces.EventListener
import com.novage.p2pml.api.models.CoreEventMap

internal class EventEmitter : CoreEventEmitter {
    private val listeners = mutableMapOf<CoreEventMap<*>, MutableList<EventListener<*>>>()

    override fun <T> addEventListener(event: CoreEventMap<T>, listener: EventListener<T>) {
        val list = listeners.getOrPut(event) { mutableListOf() }
        list.add(listener)
    }

    override fun <T> removeEventListener(event: CoreEventMap<T>, listener: EventListener<T>) {
        listeners[event]?.let { list ->
            list.remove(listener)

            if (list.isEmpty()) {
                listeners.remove(event)
            }
        }
    }

    override fun <T> emit(event: CoreEventMap<T>, data: T) {
        listeners[event]?.forEach {
            @Suppress("UNCHECKED_CAST")
            (it as EventListener<T>).onEvent(data)
        }
    }

    override fun removeAllListeners() {
        listeners.clear()
    }

    override fun getSubscribedEventNames(): List<String> = listeners.filterValues {
        it.isNotEmpty()
    }.keys.map { it.eventName }

    override fun <T> hasListeners(event: CoreEventMap<T>): Boolean = listeners[event]?.isNotEmpty() == true
}
