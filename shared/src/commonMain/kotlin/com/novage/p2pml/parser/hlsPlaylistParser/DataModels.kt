package com.novage.p2pml.parser.hlsPlaylistParser

import kotlinx.serialization.Serializable

abstract class HlsPlaylist(val baseUri: String)

class HlsMultivariantPlaylist(
    baseUri: String,
    val variants: List<Variant>,
    val videos: List<Rendition>,
    val audios: List<Rendition>,
    val subtitles: List<Rendition>,
    val closedCaptions: List<Rendition>,
) : HlsPlaylist(baseUri)

class HlsMediaPlaylist(
    baseUri: String,
    val mediaSequence: Long,
    val hasEndTag: Boolean,
    val segments: List<Segment>,
) : HlsPlaylist(baseUri)

data class Variant(
    val url: String,
    val urlInManifest: String,
    val videoGroupId: String? = null,
    val audioGroupId: String? = null,
    val subtitleGroupId: String? = null,
    val captionGroupId: String? = null,
)

data class Rendition(
    val url: String?,
    val urlInManifest: String?,
    val groupId: String,
    val name: String,
)

data class InitializationSegment(val url: String, val absoluteUrl: String)

data class Segment(
    val url: String,
    val absoluteUrl: String,
    val byteRangeOffset: Long,
    val byteRangeLength: Long,
    val durationUs: Long,
    val initializationSegment: InitializationSegment?,
) {
    val byteRange: ByteRange?
        get() =
            if (byteRangeLength != -1L) {
                ByteRange(byteRangeOffset, byteRangeOffset + byteRangeLength - 1)
            } else {
                null
            }

    val runtimeUrl =
        if (byteRange != null) {
            "$absoluteUrl|${byteRange!!.start}-${byteRange!!.end}"
        } else {
            absoluteUrl
        }
}

@Serializable data class ByteRange(val start: Long, val end: Long)
