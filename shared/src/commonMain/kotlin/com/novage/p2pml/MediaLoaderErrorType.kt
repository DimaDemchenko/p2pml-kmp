package com.novage.p2pml

/**
 * Categorizes errors that occur during the media loading process.
 * Use this to determine if you need to retry, show a UI message, or restart playback.
 */
enum class MediaLoaderErrorType {
    /**
     * The player failed to fetch the Master or Media Playlist.
     */
    MANIFEST_LOAD_ERROR,

    /**
     * The playlist content was invalid or unsupported.
     */
    MANIFEST_PARSE_ERROR,

    /**
     * A video segment failed to download via both P2P and HTTP.
     */
    SEGMENT_DOWNLOAD_ERROR,

    /**
     * The internal engine (WebView) crashed or failed to load.
     */
    ENGINE_RUNTIME_ERROR,

    /**
     * Critical: The internal proxy server failed to start.
     */
    ENGINE_STARTUP_ERROR
}
