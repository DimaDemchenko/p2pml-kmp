package com.novage.p2pml.internal.parser.hlsPlaylistParser

import com.novage.p2pml.api.models.ByteRange
import com.novage.p2pml.api.models.Segment
import kotlinx.serialization.Serializable

internal sealed interface HlsPlaylist {
    val baseUri: String
}

internal data class ParsedUrl(val original: String, val absolute: String)

internal data class HlsMultivariantPlaylist(
    override val baseUri: String,
    val variants: List<Variant>,
    val videos: List<Rendition>,
    val audios: List<Rendition>,
    val subtitles: List<Rendition>,
    val closedCaptions: List<Rendition>,
    val sessionKeyUrls: List<ParsedUrl>
) : HlsPlaylist

internal data class HlsMediaPlaylist(
    override val baseUri: String,
    val mediaSequence: Long,
    val hasEndTag: Boolean,
    val hlsSegments: List<HlsSegment>,
    val parts: List<ParsedUrl>,
    val preloadHints: List<ParsedUrl>,
    val renditionReports: List<ParsedUrl>
) : HlsPlaylist

internal data class Variant(
    val url: ParsedUrl,
    val videoGroupId: String? = null,
    val audioGroupId: String? = null,
    val subtitleGroupId: String? = null,
    val captionGroupId: String? = null,
    val isIFrame: Boolean = false
)

internal data class Rendition(val url: ParsedUrl?, val groupId: String, val name: String)

internal data class InitializationSegment(val url: ParsedUrl)

internal data class HlsSegment(
    val url: ParsedUrl,
    val byteRangeOffset: Long,
    val byteRangeLength: Long,
    val durationUs: Long,
    val initializationSegment: InitializationSegment?,
    val encryptionKey: ParsedUrl?
) {
    val byteRange: ByteRange?
        get() = if (byteRangeLength != -1L) ByteRange(byteRangeOffset, byteRangeOffset + byteRangeLength - 1) else null

    val runtimeUrl = if (byteRange != null) "${url.absolute}|${byteRange!!.start}-${byteRange!!.end}" else url.absolute
}

@Serializable
internal data class UpdateStreamParams(
    val streamRuntimeId: String,
    val addSegments: List<Segment>,
    val removeSegmentsIds: List<String>,
    val isLive: Boolean
)

@Serializable
internal data class Stream(val runtimeId: String, val type: String, val index: Int)
