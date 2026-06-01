package com.novage.p2pml.internal.parser.hlsPlaylistParser

private val AUDIO_CODEC_PREFIXES = listOf("mp4a", "ac-3", "ec-3", "opus", "vorbis", "flac")

/**
 * Extracts the video codec from a combined HLS codecs string
 * (e.g. "avc1.4D401F,mp4a.40.2" → "avc1.4D401F")
 * by filtering out known audio codec prefixes.
 *
 * Returns `null` if [codecs] is null or contains only audio codecs.
 */
internal fun extractVideoCodec(codecs: String?): String? {
    if (codecs == null) return null
    val filtered = codecs.split(",")
        .map { it.trim() }
        .filter { codec -> AUDIO_CODEC_PREFIXES.none { codec.lowercase().startsWith(it) } }
    return filtered.joinToString(",").ifEmpty { null }
}
