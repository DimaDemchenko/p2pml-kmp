package com.novage.p2pml.api.events

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the source from which a segment or chunk was downloaded.
 */
@Serializable
enum class DownloadSource(val value: String) {
    /** The data was loaded via a peer-to-peer connection. */
    @OptIn(ExperimentalObjCName::class)
    @SerialName("p2p")
    @ObjCName(swiftName = "p2p")
    P2P("p2p"),

    /** The data was loaded via HTTP. */
    @OptIn(ExperimentalObjCName::class)
    @SerialName("http")
    @ObjCName(swiftName = "http")
    HTTP("http");

    companion object {
        /**
         * Resolves the engine's string representation to a [DownloadSource], or `null` for
         * unrecognized values. Never throws: an unknown value (e.g. a newer engine build behind
         * a custom engine URL emitting a new source type) must degrade to a dropped stats event,
         * not an exception on the WebView bridge thread — that would crash the host app.
         */
        fun fromValue(value: String): DownloadSource? = entries.firstOrNull { it.value == value }
    }
}
