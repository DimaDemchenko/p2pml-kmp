package com.novage.p2pml.internal.server.services

import com.novage.p2pml.internal.parser.HlsManifestManager
import com.novage.p2pml.internal.parser.hls.ReferenceStreamManifests
import com.novage.p2pml.internal.server.config.LocalUrlFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Pins the manifest transaction: which fetches trigger the reset callback, and which engine
 * sync sequence each kind of fetch produces. Uses the real parser/tracker pipeline and the
 * reference-stream fixtures; the engine call log asserts order, not just presence.
 */
class ManifestServiceTest {

    private val hevcBase = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc"
    private val hevcMasterUrl = "$hevcBase/master.m3u8"
    private val hevcMediaUrl = "$hevcBase/v5/prog_index.m3u8"
    private val muxMediaUrl = "https://test-streams.mux.dev/x36xhzz/url_0/193039199_mp4_h264_aac_hd_7.m3u8"

    private class Harness {
        val engine = RecordingP2PEngine()
        val manifestManager = HlsManifestManager(LocalUrlFactory(sessionToken = "tok").apply { setPort(8080) })
        var resetCount = 0

        // Mirrors the production wiring in P2PSessionFactory: an untracked manifest resets the
        // parser state alongside the service's own latch.
        val service = ManifestService(manifestManager, engine) {
            resetCount++
            manifestManager.reset()
        }
    }

    @Test
    fun firstManifestResetsOnceAndPerformsInitialEngineSetup() = runTest {
        val h = Harness()

        val rewritten = h.service.processManifest(muxMediaUrl, muxMediaUrl, ReferenceStreamManifests.MUX_MEDIA)

        assertEquals(1, h.resetCount)
        // Initial setup: manifest identity, the full stream list (one ad-hoc stream), then the
        // 64-segment update — in that order.
        assertEquals(
            listOf("setManifestUrl", "sendAllStreams:1", "sendStream:+64-0"),
            h.engine.calls
        )
        assertTrue(rewritten.contains("http://127.0.0.1:8080/tok/segment/"))
    }

    @Test
    fun trackedRefreshSendsOnlyAnIncrementalUpdate() = runTest {
        val h = Harness()
        h.service.processManifest(muxMediaUrl, muxMediaUrl, ReferenceStreamManifests.MUX_MEDIA)

        h.service.processManifest(muxMediaUrl, muxMediaUrl, ReferenceStreamManifests.MUX_MEDIA)

        assertEquals(1, h.resetCount, "a refresh of a tracked manifest must not reset state")
        // The identical VOD refresh adds nothing new; only an (empty) stream update is sent.
        assertEquals(
            listOf("setManifestUrl", "sendAllStreams:1", "sendStream:+64-0", "sendStream:+0-0"),
            h.engine.calls
        )
    }

    @Test
    fun variantDeclaredByTheMasterDoesNotResetState() = runTest {
        val h = Harness()

        h.service.processManifest(hevcMasterUrl, hevcMasterUrl, ReferenceStreamManifests.APPLE_HEVC_MASTER)

        assertEquals(1, h.resetCount)
        // A master has no segment update of its own: identity + the 21 declared streams.
        assertEquals(listOf("setManifestUrl", "sendAllStreams:21"), h.engine.calls)

        h.service.processManifest(hevcMediaUrl, hevcMediaUrl, ReferenceStreamManifests.APPLE_HEVC_MEDIA)

        // The variant fetch belongs to the same stream: no reset, no re-setup — just its segments.
        assertEquals(1, h.resetCount)
        assertEquals(
            listOf("setManifestUrl", "sendAllStreams:21", "sendStream:+76-0"),
            h.engine.calls
        )
    }

    @Test
    fun switchingToAForeignManifestResetsAndRunsSetupAgain() = runTest {
        val h = Harness()
        h.service.processManifest(muxMediaUrl, muxMediaUrl, ReferenceStreamManifests.MUX_MEDIA)

        h.service.processManifest(hevcMasterUrl, hevcMasterUrl, ReferenceStreamManifests.APPLE_HEVC_MASTER)

        assertEquals(2, h.resetCount, "an untracked manifest is a stream switch and must reset")
        assertEquals(
            listOf(
                "setManifestUrl",
                "sendAllStreams:1",
                "sendStream:+64-0",
                "setManifestUrl",
                "sendAllStreams:21"
            ),
            h.engine.calls
        )
    }
}
