package com.novage.p2pml.internal.parser.encoding

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal fun encodeToUrlSafeBase64(value: String): String = Base64.UrlSafe.encode(value.encodeToByteArray())

@OptIn(ExperimentalEncodingApi::class)
internal fun decodeFromUrlSafeBase64(urlSafeBase64: String): String =
    Base64.UrlSafe.decode(urlSafeBase64).decodeToString()
