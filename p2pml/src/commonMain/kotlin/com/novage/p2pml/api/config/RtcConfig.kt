package com.novage.p2pml.api.config

import kotlinx.serialization.Serializable

/**
 * A single ICE (STUN/TURN) server entry for the WebRTC peer connection.
 *
 * @property urls One or more STUN/TURN URLs for this server.
 * @property username Optional username for TURN authentication.
 * @property credential Optional credential for TURN authentication.
 */
@Serializable
data class IceServer(val urls: List<String>, val username: String? = null, val credential: String? = null)

/**
 * WebRTC configuration for the P2P engine — primarily the set of ICE servers used to
 * establish peer connections.
 *
 * @property iceServers The ICE servers to use; `null` lets the engine apply its default.
 */
@Serializable
data class RtcConfig(val iceServers: List<IceServer>? = null)
