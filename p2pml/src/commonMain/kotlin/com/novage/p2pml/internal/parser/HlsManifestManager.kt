package com.novage.p2pml.internal.parser

import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMediaPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMultivariantPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsPlaylistParser
import com.novage.p2pml.internal.server.config.LocalUrlFactory
import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

internal class HlsManifestManager(
    playbackProvider: PlaybackProvider,
    urlFactory: LocalUrlFactory
) {
    private val logger = CoreLogger("HlsManifestManager")
    private val parser = HlsPlaylistParser()
    private val tracker = HlsStreamStateTracker(playbackProvider)
    private val rewriter = LocalHlsUrlRewriter(urlFactory)
    private val mutex = Mutex()
    private var parseSessionGeneration = 0

    suspend fun getModifiedManifest(originalManifest: String, manifestUrl: String): String {
        val startGeneration = mutex.withLock { parseSessionGeneration }
        
        logger.d { "Processing manifest: $manifestUrl (Length: ${originalManifest.length})" }
        val result = parser.parse(manifestUrl, originalManifest, rewriter)

        mutex.withLock {
            if (parseSessionGeneration != startGeneration) {
                logger.d { "Discarding tracking updates from stale parsed session due to reset: $manifestUrl" }
                return result.rewrittenManifest
            }
            
            when (val hlsPlaylist = result.playlist) {
                is HlsMediaPlaylist -> {
                    logger.d { "Type: Media Playlist. Live: ${!hlsPlaylist.hasEndTag}" }
                    tracker.postProcessMediaPlaylist(manifestUrl, hlsPlaylist)
                }
                is HlsMultivariantPlaylist -> {
                    logger.d { "Type: Multivariant (Master) Playlist" }
                    tracker.postProcessMultivariantPlaylist(manifestUrl, hlsPlaylist)
                }
            }
        }
        return result.rewrittenManifest
    }

    suspend fun isCurrentSegment(segmentUrl: String): Boolean = mutex.withLock {
        tracker.isCurrentSegment(segmentUrl)
    }

    suspend fun isManifestTracked(manifestUrl: String): Boolean = mutex.withLock {
        tracker.isManifestTracked(manifestUrl)
    }

    suspend fun getUpdateStreamParamsJson(variantUrl: String): String? = mutex.withLock {
        tracker.getUpdateStreamParams(variantUrl)?.let { Json.encodeToString(it) }
    }

    suspend fun getStreamsJson(): String = mutex.withLock {
        Json.encodeToString(tracker.getStreams())
    }

    suspend fun reset() = mutex.withLock {
        parseSessionGeneration++
        tracker.reset()
    }
}
