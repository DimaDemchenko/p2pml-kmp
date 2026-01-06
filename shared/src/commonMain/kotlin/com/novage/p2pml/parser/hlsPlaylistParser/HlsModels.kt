package com.novage.p2pml.parser.hlsPlaylistParser

import com.novage.p2pml.domain.models.ByteRange
import com.novage.p2pml.domain.models.Segment
import kotlinx.serialization.Serializable

abstract class HlsPlaylist(val baseUri: String)

internal class HlsMultivariantPlaylist(
    baseUri: String,
    val variants: List<Variant>,
    val videos: List<Rendition>,
    val audios: List<Rendition>,
    val subtitles: List<Rendition>,
    val closedCaptions: List<Rendition>
) : HlsPlaylist(baseUri)

internal class HlsMediaPlaylist(
    baseUri: String,
    val mediaSequence: Long,
    val hasEndTag: Boolean,
    val hlsSegments: List<HlsSegment>
) : HlsPlaylist(baseUri)

data class Variant(
    val url: String,
    val urlInManifest: String,
    val videoGroupId: String? = null,
    val audioGroupId: String? = null,
    val subtitleGroupId: String? = null,
    val captionGroupId: String? = null
)

data class Rendition(val url: String?, val urlInManifest: String?, val groupId: String, val name: String)

data class InitializationSegment(val url: String, val absoluteUrl: String)

internal data class HlsSegment(
    val url: String,
    val absoluteUrl: String,
    val byteRangeOffset: Long,
    val byteRangeLength: Long,
    val durationUs: Long,
    val initializationSegment: InitializationSegment?
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

@Serializable
internal data class UpdateStreamParams(
    val streamRuntimeId: String,
    val addSegments: List<Segment>,
    val removeSegmentsIds: List<String>,
    val isLive: Boolean
)

@Serializable internal data class Stream(val runtimeId: String, val type: String, val index: Int)
