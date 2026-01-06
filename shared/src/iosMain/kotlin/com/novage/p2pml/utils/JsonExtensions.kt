package com.novage.p2pml.utils

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import platform.Foundation.NSDictionary
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun dictionaryToJson(dict: NSDictionary): NSString {
    val data =
        NSJSONSerialization.dataWithJSONObject(obj = dict, options = 0u, error = null)
            ?: error("Failed to serialize NSDictionary to JSON data")

    val str =
        NSString.create(data = data, encoding = NSUTF8StringEncoding)
            ?: error("Failed to convert JSON data to UTF-8 string")

    return str
}

internal inline fun <reified T> Json.decodeFromNSDictionary(dict: NSDictionary): T {
    val json = dictionaryToJson(dict)
    return decodeFromString(json.toString())
}
