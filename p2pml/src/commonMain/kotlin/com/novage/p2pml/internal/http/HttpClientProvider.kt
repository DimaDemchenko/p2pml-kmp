package com.novage.p2pml.internal.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout

internal const val MANIFEST_REQUEST_TIMEOUT_MS = 15_000L

private const val CONNECT_TIMEOUT_MS = 10_000L
private const val SOCKET_TIMEOUT_MS = 30_000L

internal expect fun createHttpClient(): HttpClient

internal fun HttpClientConfig<*>.applyProxyClientDefaults() {
    expectSuccess = true
    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        socketTimeoutMillis = SOCKET_TIMEOUT_MS
    }
}
