package com.novage.p2pml.internal.playback

import com.novage.p2pml.api.events.Segment
import com.novage.p2pml.internal.parser.TimelineBounds

/**
 * The slice of parser state [SequenceStateTracker] needs: where the segment timeline currently sits,
 * and which segment a requested runtime id belongs to.
 *
 * Declared next to its consumer rather than on the parser, so the tracker depends only on what it
 * actually uses — which also makes its seek and catch-up behaviour testable without standing up a
 * manifest pipeline.
 */
internal interface PlaybackTimelineSource {
    suspend fun getMainTimelineBounds(): TimelineBounds?

    suspend fun getSegmentWithManifestByUrl(runtimeId: String): Pair<String, Segment>?
}
