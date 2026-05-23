package com.novage.p2pml.internal.parser.hlsPlaylistParser

import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom

internal fun resolveAbsoluteUrl(baseUri: String, referenceUri: String): String {
    if (baseUri.isEmpty()) return referenceUri
    if (referenceUri.isEmpty()) return baseUri

    return try {
        URLBuilder().apply {
            takeFrom(baseUri)
            takeFrom(referenceUri)
        }.buildString()
    } catch (_: Exception) {
        referenceUri // Fallback
    }
}
