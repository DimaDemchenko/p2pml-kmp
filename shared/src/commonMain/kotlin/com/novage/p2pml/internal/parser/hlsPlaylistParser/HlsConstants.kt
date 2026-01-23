package com.novage.p2pml.internal.parser.hlsPlaylistParser

internal object HlsConstants {
    const val MICROS_PER_SECOND = 1_000_000L
    const val PLAYLIST_HEADER: String = "#EXTM3U"
    const val TAG_VERSION: String = "#EXT-X-VERSION"
    const val TAG_PLAYLIST_TYPE: String = "#EXT-X-PLAYLIST-TYPE"
    const val TAG_DEFINE: String = "#EXT-X-DEFINE"
    const val TAG_SERVER_CONTROL: String = "#EXT-X-SERVER-CONTROL"
    const val TAG_STREAM_INF: String = "#EXT-X-STREAM-INF"
    const val TAG_PART_INF: String = "#EXT-X-PART-INF"
    const val TAG_PART: String = "#EXT-X-PART"
    const val TAG_I_FRAME_STREAM_INF: String = "#EXT-X-I-FRAME-STREAM-INF"
    const val TAG_IFRAME: String = "#EXT-X-I-FRAMES-ONLY"
    const val TAG_MEDIA: String = "#EXT-X-MEDIA"
    const val TAG_TARGET_DURATION: String = "#EXT-X-TARGETDURATION"
    const val TAG_DISCONTINUITY: String = "#EXT-X-DISCONTINUITY"
    const val TAG_DISCONTINUITY_SEQUENCE: String = "#EXT-X-DISCONTINUITY-SEQUENCE"
    const val TAG_PROGRAM_DATE_TIME: String = "#EXT-X-PROGRAM-DATE-TIME"
    const val TAG_INIT_SEGMENT: String = "#EXT-X-MAP"
    const val TAG_INDEPENDENT_SEGMENTS: String = "#EXT-X-INDEPENDENT-SEGMENTS"
    const val TAG_MEDIA_DURATION: String = "#EXTINF"
    const val TAG_MEDIA_SEQUENCE: String = "#EXT-X-MEDIA-SEQUENCE"
    const val TAG_START: String = "#EXT-X-START"
    const val TAG_ENDLIST: String = "#EXT-X-ENDLIST"
    const val TAG_KEY: String = "#EXT-X-KEY"
    const val TAG_SESSION_KEY: String = "#EXT-X-SESSION-KEY"
    const val TAG_BYTERANGE: String = "#EXT-X-BYTERANGE"
    const val TAG_GAP: String = "#EXT-X-GAP"
    const val TAG_SKIP: String = "#EXT-X-SKIP"
    const val TAG_PRELOAD_HINT: String = "#EXT-X-PRELOAD-HINT"
    const val TAG_RENDITION_REPORT: String = "#EXT-X-RENDITION-REPORT"

    // Type constants.
    const val TYPE_AUDIO: String = "AUDIO"
    const val TYPE_VIDEO: String = "VIDEO"
    const val TYPE_SUBTITLES: String = "SUBTITLES"
    const val TYPE_CLOSED_CAPTIONS: String = "CLOSED-CAPTIONS"
    const val TYPE_PART: String = "PART"
    const val TYPE_MAP: String = "MAP"

    // Method constants.
    const val METHOD_NONE: String = "NONE"
    const val METHOD_AES_128: String = "AES-128"
    const val METHOD_SAMPLE_AES: String = "SAMPLE-AES"
    const val METHOD_SAMPLE_AES_CENC: String = "SAMPLE-AES-CENC"
    const val METHOD_SAMPLE_AES_CTR: String = "SAMPLE-AES-CTR"

    // Keyformat constants.
    const val KEYFORMAT_PLAYREADY: String = "com.microsoft.playready"
    const val KEYFORMAT_IDENTITY: String = "identity"
    const val KEYFORMAT_WIDEVINE_PSSH_BINARY: String =
        "urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"
    const val KEYFORMAT_WIDEVINE_PSSH_JSON: String = "com.widevine"

    // Boolean constants.
    const val BOOLEAN_TRUE: String = "YES"
    const val BOOLEAN_FALSE: String = "NO"

    const val ATTR_CLOSED_CAPTIONS_NONE: String = "CLOSED-CAPTIONS=NONE"

    // Precompiled regex patterns.
    val REGEX_AVERAGE_BANDWIDTH: Regex = Regex("AVERAGE-BANDWIDTH=(\\d+)\\b")
    val REGEX_VIDEO: Regex = Regex("VIDEO=\"(.+?)\"")
    val REGEX_AUDIO: Regex = Regex("AUDIO=\"(.+?)\"")
    val REGEX_SUBTITLES: Regex = Regex("SUBTITLES=\"(.+?)\"")
    val REGEX_CLOSED_CAPTIONS: Regex = Regex("CLOSED-CAPTIONS=\"(.+?)\"")
    val REGEX_BANDWIDTH: Regex = Regex("[^-]BANDWIDTH=(\\d+)\\b")
    val REGEX_CHANNELS: Regex = Regex("CHANNELS=\"(.+?)\"")
    val REGEX_CODECS: Regex = Regex("CODECS=\"(.+?)\"")
    val REGEX_RESOLUTION: Regex = Regex("RESOLUTION=(\\d+x\\d+)")
    val REGEX_FRAME_RATE: Regex = Regex("FRAME-RATE=([\\d\\.]+)\\b")
    val REGEX_TARGET_DURATION: Regex = Regex("$TAG_TARGET_DURATION:(\\d+)\\b")
    val REGEX_ATTR_DURATION: Regex = Regex("DURATION=([\\d\\.]+)\\b")
    val REGEX_PART_TARGET_DURATION: Regex = Regex("PART-TARGET=([\\d\\.]+)\\b")
    val REGEX_VERSION: Regex = Regex("$TAG_VERSION:(\\d+)\\b")
    val REGEX_PLAYLIST_TYPE: Regex = Regex("$TAG_PLAYLIST_TYPE:(.+)\\b")
    val REGEX_CAN_SKIP_UNTIL: Regex = Regex("CAN-SKIP-UNTIL=([\\d\\.]+)\\b")
    val REGEX_CAN_SKIP_DATE_RANGES: Regex = compileBooleanAttrPattern("CAN-SKIP-DATERANGES")
    val REGEX_SKIPPED_SEGMENTS: Regex = Regex("SKIPPED-SEGMENTS=(\\d+)\\b")
    val REGEX_HOLD_BACK: Regex = Regex("[:|,]HOLD-BACK=([\\d\\.]+)\\b")
    val REGEX_PART_HOLD_BACK: Regex = Regex("PART-HOLD-BACK=([\\d\\.]+)\\b")
    val REGEX_CAN_BLOCK_RELOAD: Regex = compileBooleanAttrPattern("CAN-BLOCK-RELOAD")
    val REGEX_MEDIA_SEQUENCE: Regex = Regex("$TAG_MEDIA_SEQUENCE:(\\d+)\\b")
    val REGEX_MEDIA_DURATION: Regex = Regex("$TAG_MEDIA_DURATION:([\\d\\.]+)\\b")
    val REGEX_MEDIA_TITLE: Regex = Regex("$TAG_MEDIA_DURATION:[\\d\\.]+\\b,(.+)")
    val REGEX_LAST_MSN: Regex = Regex("LAST-MSN=(\\d+)\\b")
    val REGEX_LAST_PART: Regex = Regex("LAST-PART=(\\d+)\\b")
    val REGEX_TIME_OFFSET: Regex = Regex("TIME-OFFSET=(-?[\\d\\.]+)\\b")
    val REGEX_BYTERANGE: Regex = Regex("$TAG_BYTERANGE:(\\d+(?:@\\d+)?)\\b")
    val REGEX_ATTR_BYTERANGE: Regex = Regex("BYTERANGE=\"(\\d+(?:@\\d+)?)\\b\"")
    val REGEX_BYTERANGE_START: Regex = Regex("BYTERANGE-START=(\\d+)\\b")
    val REGEX_BYTERANGE_LENGTH: Regex = Regex("BYTERANGE-LENGTH=(\\d+)\\b")
    val REGEX_METHOD: Regex =
        Regex(
            "METHOD=(" +
                METHOD_NONE +
                "|" +
                METHOD_AES_128 +
                "|" +
                METHOD_SAMPLE_AES +
                "|" +
                METHOD_SAMPLE_AES_CENC +
                "|" +
                METHOD_SAMPLE_AES_CTR +
                ")\\s*(?:,|$)"
        )
    val REGEX_KEYFORMAT: Regex = Regex("KEYFORMAT=\"(.+?)\"")
    val REGEX_KEYFORMATVERSIONS: Regex = Regex("KEYFORMATVERSIONS=\"(.+?)\"")
    val REGEX_URI: Regex = Regex("URI=\"(.+?)\"")
    val REGEX_IV: Regex = Regex("IV=([^,.*]+)")
    val REGEX_TYPE: Regex =
        Regex(
            "TYPE=(" +
                TYPE_AUDIO +
                "|" +
                TYPE_VIDEO +
                "|" +
                TYPE_SUBTITLES +
                "|" +
                TYPE_CLOSED_CAPTIONS +
                ")"
        )
    val REGEX_PRELOAD_HINT_TYPE: Regex = Regex("TYPE=($TYPE_PART|$TYPE_MAP)")
    val REGEX_LANGUAGE: Regex = Regex("LANGUAGE=\"(.+?)\"")
    val REGEX_NAME: Regex = Regex("NAME=\"(.+?)\"")
    val REGEX_GROUP_ID: Regex = Regex("GROUP-ID=\"(.+?)\"")
    val REGEX_CHARACTERISTICS: Regex = Regex("CHARACTERISTICS=\"(.+?)\"")
    val REGEX_INSTREAM_ID: Regex = Regex("INSTREAM-ID=\"((?:CC|SERVICE)\\d+)\"")
    val REGEX_AUTOSELECT: Regex = compileBooleanAttrPattern("AUTOSELECT")
    val REGEX_DEFAULT: Regex = compileBooleanAttrPattern("DEFAULT")
    val REGEX_FORCED: Regex = compileBooleanAttrPattern("FORCED")
    val REGEX_INDEPENDENT: Regex = compileBooleanAttrPattern("INDEPENDENT")
    val REGEX_GAP: Regex = compileBooleanAttrPattern("GAP")
    val REGEX_PRECISE: Regex = compileBooleanAttrPattern("PRECISE")
    val REGEX_VALUE: Regex = Regex("VALUE=\"(.+?)\"")
    val REGEX_VARIABLE_REFERENCE: Regex = Regex("\\{\\$([a-zA-Z0-9\\-_]+)\\}")

    /** Compiles a regex for a boolean attribute with the given name. */
    private fun compileBooleanAttrPattern(attrName: String): Regex = Regex("$attrName=(YES|NO)\\b")
}
