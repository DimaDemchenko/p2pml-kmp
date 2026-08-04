package com.novage.p2pml.internal.parser.hls

import com.novage.p2pml.api.events.ByteRange
import com.novage.p2pml.internal.parser.LocalHlsUrlRewriter
import com.novage.p2pml.internal.parser.encoding.encodeToUrlSafeBase64
import com.novage.p2pml.internal.server.config.LocalUrlFactory
import io.ktor.http.encodeURLParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parses the battle-tested public manifests in [ReferenceStreamManifests] through the production
 * rewriter. Every expected value below was derived by inspecting the manifest text itself,
 * not from parser output — when an assertion disagrees with the parser, the parser is wrong.
 */
class HlsPlaylistParserReferenceStreamsTest {

    private fun parser(): HlsPlaylistParser {
        val urlFactory = LocalUrlFactory(sessionToken = "tok").apply { setPort(8080) }
        return HlsPlaylistParser(urlRewriter = LocalHlsUrlRewriter(urlFactory))
    }

    private fun proxiedManifestUrl(absoluteUrl: String) =
        "http://127.0.0.1:8080/tok/manifest/${absoluteUrl.encodeURLParameter()}"

    private fun proxiedSegmentUrl(runtimeId: String) =
        "http://127.0.0.1:8080/tok/segment/${encodeToUrlSafeBase64(runtimeId)}"

    // ------------------------------------------------------------------ mux Big Buck Bunny

    private val muxMasterUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    private val muxMediaUrl = "https://test-streams.mux.dev/x36xhzz/url_0/193039199_mp4_h264_aac_hd_7.m3u8"

    @Test
    fun muxMasterParsesTheFullVariantLadder() {
        val playlist = parser().parse(muxMasterUrl, ReferenceStreamManifests.MUX_MASTER).playlist
            as HlsMultivariantPlaylist

        // The manifest lists exactly five EXT-X-STREAM-INF entries, in this order.
        assertEquals(
            listOf(2149280, 246440, 460560, 836280, 6221600),
            playlist.variants.map { it.bandwidth }
        )
        assertTrue(playlist.variants.none { it.isIFrame })

        val hd = playlist.variants[0]
        assertEquals(1280, hd.width)
        assertEquals(720, hd.height)
        assertEquals("mp4a.40.2,avc1.64001f", hd.codecs)
        assertEquals(
            "https://test-streams.mux.dev/x36xhzz/url_0/193039199_mp4_h264_aac_hd_7.m3u8",
            hd.url.absolute
        )

        val fullHd = playlist.variants[4]
        assertEquals(1920, fullHd.width)
        assertEquals(1080, fullHd.height)

        assertTrue(playlist.audios.isEmpty())
        assertTrue(playlist.videos.isEmpty())
    }

    @Test
    fun muxMasterRewritesEveryVariantThroughTheProxy() {
        val result = parser().parse(muxMasterUrl, ReferenceStreamManifests.MUX_MASTER)

        listOf("url_0", "url_2", "url_4", "url_6", "url_8").forEach { dir ->
            val absolute = "https://test-streams.mux.dev/x36xhzz/$dir/" +
                ReferenceStreamManifests.MUX_MASTER.lineSequence().first { it.startsWith(dir) }.substringAfter("/")
            assertTrue(
                result.rewrittenManifest.contains(proxiedManifestUrl(absolute)),
                "variant $dir must be proxied"
            )
        }
        // No raw relative variant line may survive the rewrite.
        assertFalse(result.rewrittenManifest.contains("\nurl_0/"))
    }

    @Test
    fun muxMediaPlaylistParsesEverySegment() {
        val playlist = parser().parse(muxMediaUrl, ReferenceStreamManifests.MUX_MEDIA).playlist
            as HlsMediaPlaylist

        // No EXT-X-MEDIA-SEQUENCE tag: defaults to 0. The playlist ends with EXT-X-ENDLIST.
        assertEquals(0, playlist.mediaSequence)
        assertTrue(playlist.hasEndTag)

        // 64 EXTINF entries: 57 x 10.000s, 3 x 10.050s, 3 x 9.950s and a final 4.584s.
        val segments = playlist.hlsSegments
        assertEquals(64, segments.size)
        assertEquals(57, segments.count { it.durationUs == 10_000_000L })
        assertEquals(3, segments.count { it.durationUs == 10_050_000L })
        assertEquals(3, segments.count { it.durationUs == 9_950_000L })
        assertEquals(4_584_000L, segments.last().durationUs)

        // Plain TS segments: no byte ranges, runtime id equals the absolute URL.
        assertTrue(segments.all { it.byteRange == null })
        assertTrue(segments.all { it.runtimeUrl == it.url.absolute })

        // Relative "url_462/..." resolves against the media playlist's directory.
        assertEquals(
            "https://test-streams.mux.dev/x36xhzz/url_0/url_462/193039199_mp4_h264_aac_hd_7.ts",
            segments.first().url.absolute
        )
        assertEquals(
            "https://test-streams.mux.dev/x36xhzz/url_0/url_525/193039199_mp4_h264_aac_hd_7.ts",
            segments.last().url.absolute
        )
    }

    @Test
    fun muxMediaRewritesSegmentsAndKeepsPassthroughTags() {
        val result = parser().parse(muxMediaUrl, ReferenceStreamManifests.MUX_MEDIA)

        val firstSegment = "https://test-streams.mux.dev/x36xhzz/url_0/url_462/193039199_mp4_h264_aac_hd_7.ts"
        assertTrue(result.rewrittenManifest.contains(proxiedSegmentUrl(firstSegment)))

        assertTrue(result.rewrittenManifest.contains("#EXT-X-PLAYLIST-TYPE:VOD"))
        assertTrue(result.rewrittenManifest.contains("#EXT-X-ENDLIST"))
    }

    // ------------------------------------------------ Apple bipbop advanced (HEVC + AVC)

    private val hevcBase = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc"
    private val hevcMasterUrl = "$hevcBase/master.m3u8"
    private val hevcMediaUrl = "$hevcBase/v5/prog_index.m3u8"

    @Test
    fun appleHevcMasterParsesDuplicatedVariantsAndRenditions() {
        val playlist = parser().parse(hevcMasterUrl, ReferenceStreamManifests.APPLE_HEVC_MASTER).playlist
            as HlsMultivariantPlaylist

        // 54 EXT-X-STREAM-INF entries (18 unique URIs, each listed once per audio group) plus
        // 10 EXT-X-I-FRAME-STREAM-INF entries.
        assertEquals(64, playlist.variants.size)
        assertEquals(10, playlist.variants.count { it.isIFrame })
        val plain = playlist.variants.filter { !it.isIFrame }
        assertEquals(54, plain.size)
        assertEquals(18, plain.map { it.url.absolute }.distinct().size)
        assertEquals(3, plain.count { it.url.absolute == "$hevcBase/v5/prog_index.m3u8" })

        // Renditions: three audio groups; subtitles and closed captions are not modeled as
        // audio/video renditions.
        assertEquals(listOf("a1", "a2", "a3"), playlist.audios.map { it.groupId })
        assertTrue(playlist.videos.isEmpty())

        // #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=2190673,BANDWIDTH=2523597,
        //   CODECS="avc1.640020,mp4a.40.2",RESOLUTION=960x540,FRAME-RATE=60.000,
        //   CLOSED-CAPTIONS="cc",AUDIO="a1",SUBTITLES="sub1"
        val v5 = plain.first { it.bandwidth == 2523597 }
        assertEquals(2190673, v5.averageBandwidth)
        assertEquals(960, v5.width)
        assertEquals(540, v5.height)
        assertEquals("60.000", v5.frameRate)
        assertEquals("avc1.640020,mp4a.40.2", v5.codecs)
        assertEquals("a1", v5.audioGroupId)
        assertEquals("sub1", v5.subtitleGroupId)
        assertEquals("cc", v5.captionGroupId)

        // #EXT-X-I-FRAME-STREAM-INF:AVERAGE-BANDWIDTH=928091,BANDWIDTH=1015727,
        //   CODECS="avc1.640028",RESOLUTION=1920x1080,URI="tp5/iframe_index.m3u8"
        val iframe = playlist.variants.first { it.isIFrame && it.bandwidth == 1015727 }
        assertEquals("avc1.640028", iframe.codecs)
        assertEquals("$hevcBase/tp5/iframe_index.m3u8", iframe.url.absolute)
    }

    @Test
    fun appleHevcMasterAppliesTheProxyRoutingPolicy() {
        val result = parser().parse(hevcMasterUrl, ReferenceStreamManifests.APPLE_HEVC_MASTER)

        // Audio renditions are proxied (they are P2P streams).
        assertTrue(result.rewrittenManifest.contains(proxiedManifestUrl("$hevcBase/a1/prog_index.m3u8")))

        // Subtitles are absolutized but never proxied.
        assertTrue(result.rewrittenManifest.contains("URI=\"$hevcBase/s1/en/prog_index.m3u8\""))

        // I-frame playlists are absolutized but never proxied.
        assertTrue(result.rewrittenManifest.contains("URI=\"$hevcBase/tp5/iframe_index.m3u8\""))

        // The URI-less closed-captions rendition passes through byte-identically.
        assertTrue(
            result.rewrittenManifest.contains(
                "#EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS,GROUP-ID=\"cc\",LANGUAGE=\"en\"," +
                    "NAME=\"English\",DEFAULT=YES,AUTOSELECT=YES,INSTREAM-ID=\"CC1\""
            )
        )
    }

    @Test
    fun appleHevcMediaParsesTheByteRangeLadder() {
        val playlist = parser().parse(hevcMediaUrl, ReferenceStreamManifests.APPLE_HEVC_MEDIA).playlist
            as HlsMediaPlaylist

        assertEquals(1, playlist.mediaSequence)
        assertTrue(playlist.hasEndTag)

        // 76 segments, all slices of the same main.mp4 distinguished only by byte range.
        val segments = playlist.hlsSegments
        assertEquals(76, segments.size)
        assertTrue(segments.all { it.url.absolute == "$hevcBase/v5/main.mp4" })
        assertEquals(76, segments.map { it.runtimeUrl }.distinct().size)

        // #EXT-X-BYTERANGE:1700094@1118 -> [1118, 1701211]; the next range starts at 1701212.
        assertEquals(ByteRange(1118, 1701211), segments[0].byteRange)
        assertEquals(1701212, segments[1].byteRange?.start)
        // Last: #EXT-X-BYTERANGE:195177@151976749.
        assertEquals(ByteRange(151976749, 151976749 + 195177 - 1), segments.last().byteRange)

        // EXTINF values carry a trailing tab in this manifest; durations must still parse.
        assertEquals(7_983_330L, segments.first().durationUs)
        assertEquals(783_330L, segments.last().durationUs)
    }

    @Test
    fun appleHevcMediaRewritesMapAndByteRangeSegments() {
        val result = parser().parse(hevcMediaUrl, ReferenceStreamManifests.APPLE_HEVC_MEDIA)
        val mainMp4 = "$hevcBase/v5/main.mp4"

        // The init segment is proxied by plain URL; its BYTERANGE attribute is preserved.
        assertTrue(
            result.rewrittenManifest.contains(
                "#EXT-X-MAP:URI=\"${proxiedSegmentUrl(mainMp4)}\",BYTERANGE=\"1118@0\""
            )
        )

        // Media segments are proxied under their byte-range runtime ids.
        assertTrue(result.rewrittenManifest.contains(proxiedSegmentUrl("$mainMp4|1118-1701211")))
        assertTrue(result.rewrittenManifest.contains(proxiedSegmentUrl("$mainMp4|151976749-152171925")))

        // Byte-range tags themselves pass through for the player.
        assertTrue(result.rewrittenManifest.contains("#EXT-X-BYTERANGE:1700094@1118"))
    }

    // ------------------------------------------------- Apple Dolby Vision + Atmos example

    private val dvBase = "https://devstreaming-cdn.apple.com/videos/streaming/examples/adv_dv_atmos"
    private val dvMasterUrl = "$dvBase/main.m3u8"
    private val dvMediaUrl =
        "$dvBase/Job2dae5735-d6ca-48ca-91be-0ec0bead535c-107702578-hls_bundle_hls240/prog_index.m3u8"

    @Test
    fun appleDvMasterParsesTheHdrLadder() {
        val playlist = parser().parse(dvMasterUrl, ReferenceStreamManifests.APPLE_DV_MASTER).playlist
            as HlsMultivariantPlaylist

        // 100 EXT-X-STREAM-INF + 17 EXT-X-I-FRAME-STREAM-INF entries.
        assertEquals(117, playlist.variants.size)
        assertEquals(17, playlist.variants.count { it.isIFrame })

        // Every entry carries VIDEO-RANGE: 54 PQ among the plain variants, 8 among i-frames.
        assertEquals(54, playlist.variants.count { !it.isIFrame && it.videoRange == "PQ" })
        assertEquals(8, playlist.variants.count { it.isIFrame && it.videoRange == "PQ" })
        assertTrue(playlist.variants.all { it.videoRange == "PQ" || it.videoRange == "SDR" })

        // Ten audio renditions, including Dolby Atmos (CHANNELS="16/JOC").
        assertEquals(10, playlist.audios.size)
        assertTrue(playlist.audios.any { it.channels == "16/JOC" })
        assertTrue(playlist.videos.isEmpty())

        // #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=10803450,BANDWIDTH=18352480,VIDEO-RANGE=SDR,
        //   CODECS="avc1.640028,ec-3",RESOLUTION=1920x1080,FRAME-RATE=23.976,...,AUDIO="ec3-48-768"
        val atmosVariant = playlist.variants.first { it.bandwidth == 18352480 }
        assertEquals("avc1.640028,ec-3", atmosVariant.codecs)
        assertEquals("SDR", atmosVariant.videoRange)
        assertEquals("23.976", atmosVariant.frameRate)
        assertEquals("ec3-48-768", atmosVariant.audioGroupId)
    }

    @Test
    fun appleDvMasterPreservesCcAndDoesNotProxyForcedSubtitles() {
        val result = parser().parse(dvMasterUrl, ReferenceStreamManifests.APPLE_DV_MASTER)

        // URI-less closed captions pass through byte-identically.
        assertTrue(
            result.rewrittenManifest.contains(
                "#EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS,GROUP-ID=\"cc\",LANGUAGE=\"en\"," +
                    "NAME=\"English\",DEFAULT=YES,AUTOSELECT=YES,INSTREAM-ID=\"CC1\""
            )
        )

        // The forced-subtitles rendition is absolutized to the origin, never proxied.
        val forcedSubs = "$dvBase/Jobe9ecaff1-8802-4a35-a411-5d83d0d368e0-107675778-" +
            "Convertforced_subtitles_vttv2_en_any-en/prog_index.m3u8"
        assertTrue(result.rewrittenManifest.contains("URI=\"$forcedSubs\""))
    }

    @Test
    fun appleDvMediaParsesTheFmp4Playlist() {
        val result = parser().parse(dvMediaUrl, ReferenceStreamManifests.APPLE_DV_MEDIA)
        val playlist = result.playlist as HlsMediaPlaylist
        val mediaBase = "$dvBase/Job2dae5735-d6ca-48ca-91be-0ec0bead535c-107702578-hls_bundle_hls240"

        assertEquals(0, playlist.mediaSequence)
        assertTrue(playlist.hasEndTag)

        val segments = playlist.hlsSegments
        assertEquals(19, segments.size)
        assertEquals("$mediaBase/fileSequence1.m4s", segments.first().url.absolute)
        assertEquals("$mediaBase/fileSequence19.m4s", segments.last().url.absolute)
        assertEquals(5_463_790L, segments.first().durationUs)
        assertNull(segments.first().byteRange)

        // The fMP4 init segment is proxied; EXT-X-BITRATE hints pass through untouched.
        assertTrue(
            result.rewrittenManifest.contains(
                "#EXT-X-MAP:URI=\"${proxiedSegmentUrl("$mediaBase/fileSequence0.mp4")}\""
            )
        )
        assertTrue(result.rewrittenManifest.contains("#EXT-X-BITRATE:643"))
        assertEquals(
            19,
            Regex("#EXT-X-BITRATE:").findAll(result.rewrittenManifest).count()
        )
    }
}
