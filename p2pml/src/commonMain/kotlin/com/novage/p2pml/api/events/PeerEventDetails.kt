package com.novage.p2pml.api.events

import kotlinx.serialization.Serializable

/**
 * Represents the details of a peer in a peer-to-peer network.
 *
 * @property peerId The unique identifier for a peer in the network.
 * @property infoHash The info hash of the swarm that the peer is part of.
 * @property streamType The type of stream that the peer is connected to.
 */
@Serializable data class PeerDetails(val peerId: String, val infoHash: String, val streamType: String)

/**
 * Represents the details of a peer error event.
 *
 * @property peerId The unique identifier for a peer in the network.
 * @property infoHash The info hash of the swarm that the peer is part of.
 * @property streamType The type of stream that the peer is connected to.
 * @property error The error that occurred during the peer-to-peer connection.
 */
@Serializable
data class PeerErrorDetails(val peerId: String, val infoHash: String, val streamType: String, val error: JsError)

/**
 * Represents the details of a peer warning event.
 *
 * @property peerId The unique identifier for a peer in the network.
 * @property infoHash The info hash of the swarm that the peer is part of.
 * @property streamType The type of stream that the peer is connected to.
 * @property warning The warning that occurred during the peer-to-peer connection.
 */
@Serializable
data class PeerWarningDetails(val peerId: String, val infoHash: String, val streamType: String, val warning: JsError)

/**
 * Represents the details of a peer connection error event.
 *
 * @property peerId The unique identifier for a peer in the network.
 * @property infoHash The info hash of the swarm that the peer is connected to.
 * @property streamType The type of stream that the peer is connected to.
 * @property trackerUrl The tracker URL that the peer was discovered from.
 * @property error The error that occurred while establishing the peer connection.
 */
@Serializable
data class PeerConnectErrorDetails(
    val peerId: String,
    val infoHash: String,
    val streamType: String,
    val trackerUrl: String,
    val error: JsError
)
