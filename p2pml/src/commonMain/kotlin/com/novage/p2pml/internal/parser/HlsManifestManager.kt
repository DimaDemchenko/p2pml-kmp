package com.novage.p2pml.internal.parser

import com.novage.p2pml.api.events.Segment
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMediaPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMultivariantPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsPlaylistParser
import com.novage.p2pml.internal.parser.hlsPlaylistParser.Stream
import com.novage.p2pml.internal.parser.hlsPlaylistParser.UpdateStreamParams
import com.novage.p2pml.internal.server.config.LocalUrlFactory
import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class HlsManifestManager(urlFactory: LocalUrlFactory) {
    private val logger = CoreLogger("HlsManifestManager")
    private val rewriter = LocalHlsUrlRewriter(urlFactory)
    private val parser = HlsPlaylistParser(urlRewriter = rewriter)
    private val tracker = HlsStreamStateTracker()
    private val mutex = Mutex()

    /** EXT-X-DEFINE variables of the tracked multivariant playlist; media playlists resolve IMPORT against them. */
    private var multivariantVariables: Map<String, String> = emptyMap()

    /**
     * Parses and rewrites a manifest. [manifestUrl] is the identity the stream is tracked under —
     * the URL the player requested, stable across sessions and refreshes. [resolutionBaseUrl] is
     * the URL the content was actually served from (after HTTP redirects) and is only used to
     * resolve relative URLs inside the playlist. Keying by the redirect target instead would make
     * every redirected fetch look like new content.
     */
    suspend fun getModifiedManifest(
        originalManifest: String,
        manifestUrl: String,
        resolutionBaseUrl: String = manifestUrl
    ): String {
        logger.d { "Processing manifest: $manifestUrl (Length: ${originalManifest.length})" }
        if (resolutionBaseUrl != manifestUrl) {
            logger.d { "Manifest served via redirect. Resolving relative URLs against: $resolutionBaseUrl" }
        }

        val parentVariables = mutex.withLock { multivariantVariables }
        val result = parser.parse(resolutionBaseUrl, originalManifest, parentVariables)

        return mutex.withLock {
            when (val hlsPlaylist = result.playlist) {
                is HlsMediaPlaylist -> {
                    logger.d { "Type: Media Playlist. Live: ${!hlsPlaylist.hasEndTag}" }
                    tracker.postProcessMediaPlaylist(manifestUrl, hlsPlaylist)
                }

                is HlsMultivariantPlaylist -> {
                    logger.d { "Type: Multivariant (Master) Playlist" }
                    multivariantVariables = hlsPlaylist.variables
                    tracker.postProcessMultivariantPlaylist(manifestUrl, hlsPlaylist)
                }
            }
            result.rewrittenManifest
        }
    }

    suspend fun isCurrentSegment(segmentUrl: String): Boolean = mutex.withLock {
        tracker.isCurrentSegment(segmentUrl)
    }

    suspend fun isManifestTracked(manifestUrl: String): Boolean = mutex.withLock {
        tracker.isManifestTracked(manifestUrl)
    }

    suspend fun getSegmentWithManifestByUrl(runtimeId: String): Pair<String, Segment>? = mutex.withLock {
        tracker.getSegmentWithManifestByUrl(runtimeId)
    }

    suspend fun getUpdateStreamParams(variantUrl: String): UpdateStreamParams? = mutex.withLock {
        tracker.getUpdateStreamParams(variantUrl)
    }

    suspend fun getStreams(): List<Stream> = mutex.withLock {
        tracker.getStreams()
    }

    suspend fun reset() = mutex.withLock {
        multivariantVariables = emptyMap()
        tracker.reset()
    }
}
