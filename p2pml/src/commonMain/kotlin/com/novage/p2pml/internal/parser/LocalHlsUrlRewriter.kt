package com.novage.p2pml.internal.parser

import com.novage.p2pml.api.models.ByteRange
import com.novage.p2pml.internal.parser.encoding.encodeToUrlSafeBase64
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsUrlRewriter
import com.novage.p2pml.internal.parser.hlsPlaylistParser.ParsedUrl
import com.novage.p2pml.internal.parser.hlsPlaylistParser.TYPE_CLOSED_CAPTIONS
import com.novage.p2pml.internal.parser.hlsPlaylistParser.TYPE_SUBTITLES
import com.novage.p2pml.internal.server.config.LocalUrlFactory
import com.novage.p2pml.internal.utils.buildSegmentRuntimeId
import io.ktor.http.encodeURLParameter

internal class LocalHlsUrlRewriter(private val urlFactory: LocalUrlFactory) : HlsUrlRewriter {
    override fun rewriteVariantUrl(url: ParsedUrl, isIFrame: Boolean): String =
        if (isIFrame) url.absolute else urlFactory.buildManifestUrl(url.absolute.encodeURLParameter())

    override fun rewriteRenditionUrl(url: ParsedUrl, type: String): String =
        if (type == TYPE_SUBTITLES || type == TYPE_CLOSED_CAPTIONS) {
            url.absolute
        } else {
            urlFactory.buildManifestUrl(url.absolute.encodeURLParameter())
        }

    override fun rewriteSessionKeyUrl(url: ParsedUrl): String = url.absolute
    override fun rewriteKeyUrl(url: ParsedUrl): String = url.absolute
    override fun rewriteLowLatencyUrl(url: ParsedUrl): String = url.absolute

    override fun rewriteSegmentUrl(url: ParsedUrl, byteRange: ByteRange?): String {
        val payload = buildSegmentRuntimeId(url.absolute, byteRange)
        return urlFactory.buildSegmentUrl(encodeToUrlSafeBase64(payload))
    }

    override fun rewriteInitSegmentUrl(url: ParsedUrl): String =
        urlFactory.buildSegmentUrl(encodeToUrlSafeBase64(url.absolute))
}
