package com.novage.p2pml.api.errors

/**
 * Categorizes errors that occur during the media loading process.
 * Use this to determine if you need to retry, show a UI message, or restart playback.
 */
enum class P2PMediaLoaderErrorType {
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
     * The internal proxy server failed to start.
     */
    ENGINE_STARTUP_ERROR,

    /**
     * The P2P Media Loader was accessed before initialization completed.
     */
    CORE_NOT_INITIALIZED_ERROR
}

/**
 * Exception thrown when the P2P Media Loader encounters a startup or runtime failure.
 *
 * This exception is used for initialization problems as well as manifest, segment download,
 * and engine runtime errors. Inspect [type] to determine the specific failure category.
 */
class P2PMediaLoaderException(val type: P2PMediaLoaderErrorType, message: String, cause: Throwable? = null) :
    Exception(message, cause)
