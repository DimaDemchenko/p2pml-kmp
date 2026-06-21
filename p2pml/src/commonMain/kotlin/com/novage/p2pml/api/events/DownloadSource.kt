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
        fun fromValue(value: String): DownloadSource = entries.first { it.value == value }
    }
}
