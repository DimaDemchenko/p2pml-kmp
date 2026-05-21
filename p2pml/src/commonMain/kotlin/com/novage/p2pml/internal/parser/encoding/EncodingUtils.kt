package com.novage.p2pml.internal.parser.encoding

import kotlin.io.encoding.Base64

internal fun encodeToUrlSafeBase64(value: String): String = Base64.UrlSafe.encode(value.encodeToByteArray())

internal fun decodeFromUrlSafeBase64(urlSafeBase64: String): String =
    Base64.UrlSafe.decode(urlSafeBase64).decodeToString()
