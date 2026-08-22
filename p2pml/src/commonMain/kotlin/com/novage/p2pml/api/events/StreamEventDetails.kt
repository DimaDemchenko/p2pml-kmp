package com.novage.p2pml.api.events

import kotlinx.serialization.Serializable

/**
 * The raw manifest properties that define a stream's identity in the swarm.
 *
 * Peers whose normalized properties match are treated as the same stream, so these values
 * decide which swarm a rendition joins. They are read from the multivariant playlist:
 * variants contribute [bitrate], [codecs], [width], [height], [frameRate] and [videoRange];
 * audio renditions contribute [language], [channels] and [name].
 *
 * @property bitrate The variant's declared bandwidth in bits per second.
 * @property codecs The RFC 6381 codec list, unnormalized.
 * @property width The variant's frame width in pixels.
 * @property height The variant's frame height in pixels.
 * @property language The rendition's language tag.
 * @property channels The rendition's audio channel descriptor, e.g. "2" or "6/JOC".
 * @property name The rendition's display name.
 * @property frameRate The variant's declared frame rate.
 * @property videoRange The variant's dynamic range, e.g. "SDR" or "PQ".
 */
@Serializable
data class StreamProperties(
    val bitrate: Int? = null,
    val codecs: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val language: String? = null,
    val channels: String? = null,
    val name: String? = null,
    val frameRate: String? = null,
    val videoRange: String? = null
)

/**
 * Represents the details of a stream that failed to register with the engine.
 *
 * A stream that fails to register stays unknown to the engine: its segments still play, but
 * they load over plain HTTP with no P2P sharing. Registration failure is not fatal to
 * playback and the loader stays active, so this event is the only signal that a rendition is
 * silently running without P2P.
 *
 * @property runtimeId The runtime identifier of the stream that failed to register.
 * @property streamType The type of stream.
 * @property properties The raw manifest properties the stream was registered with.
 * @property error The error that caused registration to fail.
 */
@Serializable
data class StreamRegistrationErrorDetails(
    val runtimeId: String,
    val streamType: StreamType,
    val properties: StreamProperties,
    val error: JsError
)
