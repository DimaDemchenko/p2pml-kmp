package com.novage.p2pml.api.events

import kotlinx.serialization.Serializable

/**
 * Represents a range of bytes, used for specifying a segment of data to download.
 *
 * @property start The starting byte index of the range.
 * @property end The ending byte index of the range.
 */
@Serializable data class ByteRange(val start: Long, val end: Long)
