package com.novage.p2pml.internal.parser.hlsPlaylistParser

internal const val MICROS_PER_SECOND = 1_000_000L
internal const val PLAYLIST_HEADER = "#EXTM3U"
internal const val TAG_DEFINE = "#EXT-X-DEFINE"
internal const val TAG_STREAM_INF = "#EXT-X-STREAM-INF"
internal const val TAG_PART = "#EXT-X-PART"
internal const val TAG_I_FRAME_STREAM_INF = "#EXT-X-I-FRAME-STREAM-INF"
internal const val TAG_MEDIA = "#EXT-X-MEDIA"
internal const val TAG_TARGET_DURATION = "#EXT-X-TARGETDURATION"
internal const val TAG_DISCONTINUITY = "#EXT-X-DISCONTINUITY"
internal const val TAG_PROGRAM_DATE_TIME = "#EXT-X-PROGRAM-DATE-TIME"
internal const val TAG_INIT_SEGMENT = "#EXT-X-MAP"
internal const val TAG_MEDIA_DURATION = "#EXTINF"
internal const val TAG_MEDIA_SEQUENCE = "#EXT-X-MEDIA-SEQUENCE"
internal const val TAG_ENDLIST = "#EXT-X-ENDLIST"
internal const val TAG_KEY = "#EXT-X-KEY"
internal const val TAG_SESSION_KEY = "#EXT-X-SESSION-KEY"
internal const val TAG_BYTERANGE = "#EXT-X-BYTERANGE"
internal const val TAG_PRELOAD_HINT = "#EXT-X-PRELOAD-HINT"
internal const val TAG_RENDITION_REPORT = "#EXT-X-RENDITION-REPORT"
internal const val TAG_SESSION_DATA = "#EXT-X-SESSION-DATA"
internal const val TAG_CONTENT_STEERING = "#EXT-X-CONTENT-STEERING"

// Type constants.
internal const val TYPE_AUDIO = "AUDIO"
internal const val TYPE_VIDEO = "VIDEO"
internal const val TYPE_SUBTITLES = "SUBTITLES"
internal const val TYPE_CLOSED_CAPTIONS = "CLOSED-CAPTIONS"

// Precompiled regex patterns.
internal val REGEX_AVERAGE_BANDWIDTH = Regex("AVERAGE-BANDWIDTH=(\\d+)\\b")
internal val REGEX_VIDEO = Regex("VIDEO=\"(.+?)\"")
internal val REGEX_AUDIO = Regex("AUDIO=\"(.+?)\"")
internal val REGEX_SUBTITLES = Regex("SUBTITLES=\"(.+?)\"")
internal val REGEX_CLOSED_CAPTIONS = Regex("CLOSED-CAPTIONS=\"(.+?)\"")
internal val REGEX_BANDWIDTH = Regex("[^-]BANDWIDTH=(\\d+)\\b")
internal val REGEX_CHANNELS = Regex("CHANNELS=\"(.+?)\"")
internal val REGEX_CODECS = Regex("CODECS=\"(.+?)\"")
internal val REGEX_RESOLUTION = Regex("RESOLUTION=(\\d+x\\d+)")
internal val REGEX_FRAME_RATE = Regex("FRAME-RATE=([\\d\\.]+)\\b")
internal val REGEX_VIDEO_RANGE = Regex("""VIDEO-RANGE="?([^,\s"]+)"?""")
internal val REGEX_MEDIA_SEQUENCE = Regex("$TAG_MEDIA_SEQUENCE:(\\d+)\\b")
internal val REGEX_MEDIA_DURATION = Regex("$TAG_MEDIA_DURATION:([\\d\\.]+)\\b")
internal val REGEX_BYTERANGE = Regex("$TAG_BYTERANGE:(\\d+(?:@\\d+)?)\\b")
internal val REGEX_URI = Regex("URI=\"(.+?)\"")
internal val REGEX_SERVER_URI = Regex("SERVER-URI=\"(.+?)\"")
internal val REGEX_QUERYPARAM = Regex("QUERYPARAM=\"(.+?)\"")
internal val REGEX_IMPORT = Regex("IMPORT=\"(.+?)\"")
internal val REGEX_TYPE = Regex("TYPE=($TYPE_AUDIO|$TYPE_VIDEO|$TYPE_SUBTITLES|$TYPE_CLOSED_CAPTIONS)")
internal val REGEX_LANGUAGE = Regex("LANGUAGE=\"(.+?)\"")
internal val REGEX_NAME = Regex("NAME=\"(.+?)\"")
internal val REGEX_GROUP_ID = Regex("GROUP-ID=\"(.+?)\"")
internal val REGEX_VALUE = Regex("VALUE=\"(.+?)\"")
internal val REGEX_VARIABLE_REFERENCE = Regex("\\{\\$([a-zA-Z0-9\\-_]+)\\}")
