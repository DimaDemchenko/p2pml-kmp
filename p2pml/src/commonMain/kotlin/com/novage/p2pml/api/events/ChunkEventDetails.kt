package com.novage.p2pml.api.events

/**
 * Represents the details of a chunk downloaded event.
 *
 * @property bytesLength The length of the chunk in bytes.
 * @property downloadSource The source from which the chunk was downloaded.
 * @property peerId The ID of the peer from which the chunk was downloaded (if downloaded from a
 *   peer).
 * @property streamType The type of stream that the chunk belongs to.
 * @property infoHash The info hash of the swarm that the chunk belongs to.
 */
data class ChunkDownloadedDetails(
    val bytesLength: Int,
    val downloadSource: DownloadSource,
    val peerId: String?,
    val streamType: String,
    val infoHash: String
)

/**
 * Represents the details of a chunk uploaded event.
 *
 * @property bytesLength The length of the chunk in bytes.
 * @property peerId The ID of the peer to which the chunk was uploaded.
 * @property streamType The type of stream that the chunk belongs to.
 * @property infoHash The info hash of the swarm that the chunk belongs to.
 */
data class ChunkUploadedDetails(val bytesLength: Int, val peerId: String, val streamType: String, val infoHash: String)
