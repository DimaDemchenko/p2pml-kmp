package com.novage.p2pml.internal.parser

internal interface ManifestParser<T> {
    fun parse(manifestUrl: String, manifestData: String): T
}
