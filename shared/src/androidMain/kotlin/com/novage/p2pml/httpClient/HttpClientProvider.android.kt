package com.novage.p2pml.httpClient

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.*

actual fun createHttpClient(): HttpClient = HttpClient(OkHttp)
