package com.novage.p2pml.api.events

/**
 * Represents the details of a chunk downloaded event.
 *
 * @property bytesLength The length of the chunk in bytes.
 * @property downloadSource The source from which the chunk was downloaded.
 * @property peerId The ID of the peer from which the chunk was downloaded (if downloaded from a
 *   peer).
 */
data class ChunkDownloadedDetails(val bytesLength: Int, val downloadSource: DownloadSource, val peerId: String?)

/**
 * Represents the details of a chunk uploaded event.
 *
 * @property bytesLength The length of the chunk in bytes.
 * @property peerId The ID of the peer to which the chunk was uploaded.
 */
data class ChunkUploadedDetails(val bytesLength: Int, val peerId: String)
