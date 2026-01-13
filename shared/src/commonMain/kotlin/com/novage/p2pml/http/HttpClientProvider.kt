package com.novage.p2pml.http

import io.ktor.client.HttpClient

internal expect fun createHttpClient(): HttpClient
