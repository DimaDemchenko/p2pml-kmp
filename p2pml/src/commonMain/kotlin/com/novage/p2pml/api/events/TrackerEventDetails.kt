package com.novage.p2pml.api.events

import kotlinx.serialization.Serializable

/**
 * Represents the details of a tracker error event.
 *
 * @property streamType The type of stream that the tracker is for.
 * @property error The error that occurred during the tracker request.
 */
@Serializable data class TrackerErrorDetails(val streamType: String, val error: JsError)

/**
 * Represents the details of a tracker warning event.
 *
 * @property streamType The type of stream that the tracker is for.
 * @property warning The warning that occurred during the tracker request.
 */
@Serializable data class TrackerWarningDetails(val streamType: String, val warning: JsError)

/**
 * Represents a JavaScript error.
 *
 * @property message The error message.
 * @property stack The stack trace of the error.
 */
@Serializable data class JsError(val message: String, val stack: String? = null)
