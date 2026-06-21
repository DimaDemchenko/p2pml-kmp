package com.novage.p2pml.api.events

import kotlinx.serialization.Serializable

/**
 * Represents the details of a peer in a peer-to-peer network.
 *
 * @property peerId The unique identifier for a peer in the network.
 * @property streamType The type of stream that the peer is connected to.
 */
@Serializable data class PeerDetails(val peerId: String, val streamType: String)

/**
 * Represents the details of a peer error event.
 *
 * @property peerId The unique identifier for a peer in the network.
 * @property streamType The type of stream that the peer is connected to.
 * @property error The error that occurred during the peer-to-peer connection.
 */
@Serializable
data class PeerErrorDetails(val peerId: String, val streamType: String, val error: String)
