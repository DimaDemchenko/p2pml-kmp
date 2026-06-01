package com.novage.p2pml.internal.parser.hlsPlaylistParser

import com.novage.p2pml.api.models.ByteRange
import com.novage.p2pml.api.models.Segment
import com.novage.p2pml.internal.parser.buildSegmentRuntimeId
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
    val isIFrame: Boolean = false,
    val bandwidth: Int? = null,
    val averageBandwidth: Int? = null,
    val codecs: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: String? = null,
    val videoRange: String? = null
)

internal data class Rendition(
    val url: ParsedUrl?,
    val groupId: String,
    val name: String,
    val language: String? = null,
    val channels: String? = null
)

internal data class InitializationSegment(val url: ParsedUrl)

internal data class HlsSegment(
    val url: ParsedUrl,
    val byteRangeOffset: Long,
    val byteRangeLength: Long,
    val durationUs: Long,
    val programDateTimeUs: Long?,
    val initializationSegment: InitializationSegment?,
    val encryptionKey: ParsedUrl?
) {
    val byteRange: ByteRange? = if (byteRangeLength !=
        -1L
    ) {
        ByteRange(byteRangeOffset, byteRangeOffset + byteRangeLength - 1)
    } else {
        null
    }

    val runtimeUrl = buildSegmentRuntimeId(url.absolute, byteRange)
}

@Serializable
internal data class UpdateStreamParams(
    val streamRuntimeId: String,
    val addSegments: List<Segment>,
    val removeSegmentsIds: List<String>,
    val isLive: Boolean
)

@Serializable
internal data class Stream(
    val runtimeId: String,
    val type: String,
    val bitrate: Int? = null,
    val codecs: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: String? = null,
    val videoRange: String? = null,
    val language: String? = null,
    val channels: String? = null,
    val name: String? = null
)

internal interface HlsUrlRewriter {
    fun rewriteVariantUrl(url: ParsedUrl, isIFrame: Boolean): String
    fun rewriteRenditionUrl(url: ParsedUrl, type: String): String
    fun rewriteSessionKeyUrl(url: ParsedUrl): String
    fun rewriteSegmentUrl(url: ParsedUrl, byteRange: ByteRange?): String
    fun rewriteInitSegmentUrl(url: ParsedUrl): String
    fun rewriteKeyUrl(url: ParsedUrl): String
    fun rewriteLowLatencyUrl(url: ParsedUrl): String
}

internal data class ParsedPlaylist(val playlist: HlsPlaylist, val rewrittenManifest: String)
