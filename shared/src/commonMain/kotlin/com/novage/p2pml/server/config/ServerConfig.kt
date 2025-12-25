package com.novage.p2pml.server.config

class ServerConfig {
    var port: Int = -1
        private set

    val isReady: Boolean
        get() = port != -1

    fun updatePort(newPort: Int) {
        port = newPort
    }
}