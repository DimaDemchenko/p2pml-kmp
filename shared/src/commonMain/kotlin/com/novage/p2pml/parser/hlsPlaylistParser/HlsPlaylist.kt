package com.novage.p2pml.parser.hlsPlaylistParser

abstract class HlsPlaylist(
    val baseUri: String,
    tags: List<String>,
    val hasIndependentSegments: Boolean,
) {
    val tags = tags.toList()
}
