package com.novage.p2pml.parser

import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import io.ktor.util.decodeBase64String
import io.ktor.util.encodeBase64

fun encodeUrlToBase64(url: String): String = url.encodeBase64().encodeURLParameter()

fun decodeBase64Url(encodedString: String): String =
    encodedString.decodeBase64String().decodeURLQueryComponent()

