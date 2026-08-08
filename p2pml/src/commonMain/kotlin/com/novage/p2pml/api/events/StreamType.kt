package com.novage.p2pml.api.events

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the kind of stream a segment, peer or tracker belongs to. Each kind forms its own
 * swarm, so the same peer can be reported once per stream it shares.
 */
@Serializable
enum class StreamType(val value: String) {
    /** The primary stream: the video renditions, including muxed audio and video. */
    @OptIn(ExperimentalObjCName::class)
    @SerialName("main")
    @ObjCName(swiftName = "main")
    MAIN("main"),

    /** A companion stream: the separate audio renditions of a multivariant playlist. */
    @OptIn(ExperimentalObjCName::class)
    @SerialName("secondary")
    @ObjCName(swiftName = "secondary")
    SECONDARY("secondary");

    companion object {
        /**
         * Resolves the engine's string representation to a [StreamType], or `null` for
         * unrecognized values. Never throws: an unknown value (e.g. a newer engine build behind
         * a custom engine URL emitting a new stream type) must degrade to a dropped stats event,
         * not an exception on the WebView bridge thread — that would crash the host app.
         */
        fun fromValue(value: String): StreamType? = entries.firstOrNull { it.value == value }
    }
}
