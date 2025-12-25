package com.novage.p2pml.http

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
