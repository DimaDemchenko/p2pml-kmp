package com.novage.p2pml.api.events

import kotlinx.serialization.Serializable

/**
 * Represents the details of a tracker error event.
 *
 * @property trackerUrl The tracker URL.
 * @property infoHash The info hash of the swarm that the tracker is for.
 * @property streamType The type of stream that the tracker is for.
 * @property error The error that occurred during the tracker request.
 */
@Serializable
data class TrackerErrorDetails(val trackerUrl: String, val infoHash: String, val streamType: String, val error: JsError)

/**
 * Represents the details of a tracker warning event.
 *
 * @property trackerUrl The tracker URL.
 * @property infoHash The info hash of the swarm that the tracker is for.
 * @property streamType The type of stream that the tracker is for.
 * @property warning The warning that occurred during the tracker request.
 */
@Serializable
data class TrackerWarningDetails(
    val trackerUrl: String,
    val infoHash: String,
    val streamType: String,
    val warning: JsError
)

/**
 * Represents a JavaScript error.
 *
 * @property message The error message.
 * @property stack The stack trace of the error.
 * @property type The engine's error kind when available, e.g. "http-error" for segment
 *   requests or "ERR_CONNECTION_FAILURE" for peers.
 */
@Serializable data class JsError(val message: String, val stack: String? = null, val type: String? = null)
