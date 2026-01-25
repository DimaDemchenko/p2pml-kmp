package com.novage.p2pml.internal.http

import io.ktor.client.HttpClient

internal expect fun createHttpClient(): HttpClient
