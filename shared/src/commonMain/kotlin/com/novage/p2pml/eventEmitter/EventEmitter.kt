package com.novage.p2pml.eventEmitter

fun interface EventListener<T> {
    fun onEvent(data: T)
}

class EventEmitter {
    private val listeners = mutableMapOf<CoreEventMap<*>, MutableList<EventListener<*>>>()

    fun <T> addEventListener(event: CoreEventMap<T>, listener: EventListener<T>) {
        val list = listeners.getOrPut(event) { mutableListOf() }
        list.add(listener)
    }

    fun <T> removeEventListener(event: CoreEventMap<T>, listener: EventListener<T>) {
        listeners[event]?.remove(listener)
    }

    fun <T> emit(event: CoreEventMap<T>, data: T) {
        listeners[event]?.forEach {
            @Suppress("UNCHECKED_CAST") (it as EventListener<T>).onEvent(data)
        }
    }

    fun removeAllListeners() {
        listeners.clear()
    }

    fun getSubscribedEventNames(): List<String> {
        return listeners.filterValues { it.isNotEmpty() }.keys.map { it.eventName }
    }
}
