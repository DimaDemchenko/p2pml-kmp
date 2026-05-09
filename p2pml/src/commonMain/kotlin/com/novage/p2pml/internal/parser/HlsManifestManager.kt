package com.novage.p2pml.internal.parser

import com.novage.p2pml.api.interfaces.PlaybackProvider
import com.novage.p2pml.api.models.Segment
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMediaPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsMultivariantPlaylist
import com.novage.p2pml.internal.parser.hlsPlaylistParser.HlsPlaylistParser
import com.novage.p2pml.internal.server.config.LocalUrlFactory
import com.novage.p2pml.internal.utils.CoreLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
internal class HlsManifestManager(playbackProvider: PlaybackProvider, urlFactory: LocalUrlFactory) {
    private val logger = CoreLogger("HlsManifestManager")
    private val parser = HlsPlaylistParser()
    private val tracker = HlsStreamStateTracker(playbackProvider)
    private val rewriter = LocalHlsUrlRewriter(urlFactory)
    private val mutex = Mutex()

    suspend fun getModifiedManifest(originalManifest: String, manifestUrl: String): String = mutex.withLock {
        logger.d { "Processing manifest: $manifestUrl (Length: ${originalManifest.length})" }
        val result = parser.parse(manifestUrl, originalManifest, rewriter)

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
        result.rewrittenManifest
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
        tracker.reset()
    }
}
