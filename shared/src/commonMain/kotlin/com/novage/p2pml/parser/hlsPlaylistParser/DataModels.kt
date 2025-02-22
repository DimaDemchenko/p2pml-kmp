package com.novage.p2pml.parser.hlsPlaylistParser

class HlsMultivariantPlaylist(
    baseUri: String,
    tags: List<String>,
    val variants: List<Variant>,
    val videos: List<Rendition>,
    val audios: List<Rendition>,
    val subtitles: List<Rendition>,
    val closedCaptions: List<Rendition>,
    hasIndependentSegments: Boolean,
    val variableDefinitions: Map<String, String>,
) : HlsPlaylist(baseUri, tags, hasIndependentSegments) {}

class HlsMediaPlaylist(
    baseUri: String,
    val initializationSegment: Segment?,
    val mediaSequence: Long,
    val hasEndTag: Boolean,
    val segments: List<Segment>,
) : HlsPlaylist(baseUri, emptyList(), false) {}

data class Variant(
    val url: String,
    val videoGroupId: String? = null,
    val audioGroupId: String? = null,
    val subtitleGroupId: String? = null,
    val captionGroupId: String? = null,
)

data class Rendition(val url: String?, val groupId: String, val name: String)

data class Segment(
    val url: String,
    val byteRangeOffset: Long,
    val byteRangeLength: Long,
    val durationUs: Long,
    val initializationSegment: Segment?,
)
