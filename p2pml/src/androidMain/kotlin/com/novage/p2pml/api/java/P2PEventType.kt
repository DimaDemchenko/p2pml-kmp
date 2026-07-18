package com.novage.p2pml.api.java

import com.novage.p2pml.api.events.P2PEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * P2P engine events selectable in [P2PMediaLoaderJava.addListener].
 *
 * Each entry corresponds to one [P2PEventListener] callback. Subscribing to an event turns it on
 * in the JS engine, so subscribe only to the events you consume — [CHUNK_DOWNLOADED] and
 * [CHUNK_UPLOADED] are high-frequency and cost a bridge call per transferred chunk.
 */
enum class P2PEventType(
    @get:JvmSynthetic
    internal val collect: P2PEvents.(P2PEventListener, CoroutineScope) -> Job
) {
    SEGMENT_LOADED({ listener, scope -> onSegmentLoaded.onEach(listener::onSegmentLoaded).launchIn(scope) }),

    SEGMENT_START({ listener, scope -> onSegmentStart.onEach(listener::onSegmentStart).launchIn(scope) }),

    SEGMENT_ERROR({ listener, scope -> onSegmentError.onEach(listener::onSegmentError).launchIn(scope) }),

    SEGMENT_ABORT({ listener, scope -> onSegmentAbort.onEach(listener::onSegmentAbort).launchIn(scope) }),

    PEER_CONNECT({ listener, scope -> onPeerConnect.onEach(listener::onPeerConnect).launchIn(scope) }),

    PEER_CONNECT_ERROR({ listener, scope -> onPeerConnectError.onEach(listener::onPeerConnectError).launchIn(scope) }),

    PEER_CLOSE({ listener, scope -> onPeerClose.onEach(listener::onPeerClose).launchIn(scope) }),

    PEER_ERROR({ listener, scope -> onPeerError.onEach(listener::onPeerError).launchIn(scope) }),

    PEER_WARNING({ listener, scope -> onPeerWarning.onEach(listener::onPeerWarning).launchIn(scope) }),

    CHUNK_DOWNLOADED({ listener, scope -> onChunkDownloaded.onEach(listener::onChunkDownloaded).launchIn(scope) }),

    CHUNK_UPLOADED({ listener, scope -> onChunkUploaded.onEach(listener::onChunkUploaded).launchIn(scope) }),

    TRACKER_ERROR({ listener, scope -> onTrackerError.onEach(listener::onTrackerError).launchIn(scope) }),

    TRACKER_WARNING({ listener, scope -> onTrackerWarning.onEach(listener::onTrackerWarning).launchIn(scope) })
}
