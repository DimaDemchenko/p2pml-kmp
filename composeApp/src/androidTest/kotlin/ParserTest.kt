import androidx.core.net.toUri
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist as ExoPlayerMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist as ExoPlayerMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser as ExoPlayerParser
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novage.p2pml.parser.hlsPlaylistParser.HlsMediaPlaylist as CustomMediaPlaylist
import com.novage.p2pml.parser.hlsPlaylistParser.HlsMultivariantPlaylist as CustomMultivariantPlaylist
import com.novage.p2pml.parser.hlsPlaylistParser.HlsPlaylistParser as CustomParser
import com.novage.p2pml.parser.hlsPlaylistParser.Rendition
import com.novage.p2pml.parser.hlsPlaylistParser.Segment
import com.novage.p2pml.parser.hlsPlaylistParser.Variant
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParserTest {
    private val masterManifests =
        listOf(
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8",
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/adv_dv_atmos/main.m3u8",
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8",
            "https://fcc3ddae59ed.us-west-2.playback.live-video.net/api/video/v1/us-west-2.893648527354.channel.DmumNckWFTqz.m3u8",
        )
    private val parsedMasterManifests = mutableSetOf<ExoPlayerMultivariantPlaylist>()

    private val httpClient = HttpClient(OkHttp)
    private val customParser = CustomParser()
    private val exoPlayerParser = ExoPlayerParser()

    private fun fetchManifest(url: String): String = runBlocking {
        try {
            val response: HttpResponse = httpClient.get(url)

            if (!response.status.isSuccess())
                fail("HTTP call failed with status ${response.status.value}")

            response.bodyAsText()
        } catch (e: Exception) {
            fail("Exception while fetching manifest: ${e.message}")
            ""
        }
    }

    private fun compareVariants(
        customVariants: List<Variant>,
        exoPlayerVariants: List<ExoPlayerMultivariantPlaylist.Variant>,
    ) {
        exoPlayerVariants.forEach { exoPlayerVariant ->
            if (!customVariants.none { it.url == exoPlayerVariant.url.toString() }) return@forEach

            fail("Variant URL mismatch for ${exoPlayerVariant.url}")
        }
    }

    private fun compareRenditions(
        customRenditions: List<Rendition>,
        exoPlayerRenditions: List<ExoPlayerMultivariantPlaylist.Rendition>,
    ) {
        exoPlayerRenditions.forEach { exoPlayerRendition ->
            if (!customRenditions.none { it.url == exoPlayerRendition.url.toString() })
                return@forEach

            fail("Rendition URL mismatch for ${exoPlayerRendition.url}")
        }
    }

    private fun compareSegments(
        customSegments: List<Segment>,
        exoPlayerSegments: List<ExoPlayerMediaPlaylist.Segment>,
    ) {
        exoPlayerSegments.forEachIndexed { index, exoPlayerSegment ->
            val customSegment = customSegments[index]

            if (customSegment.url != exoPlayerSegment.url)
                fail("Segment URL mismatch for ${exoPlayerSegment.url}")

            if (customSegment.durationUs != exoPlayerSegment.durationUs)
                fail(
                    "Segment duration mismatch for ${exoPlayerSegment.url} - expected ${customSegment.durationUs}, got ${exoPlayerSegment.durationUs}"
                )

            if (customSegment.byteRangeOffset != exoPlayerSegment.byteRangeOffset)
                fail(
                    "Segment -$index byte range offset mismatch expected - ${exoPlayerSegment.byteRangeOffset}, got ${customSegment.byteRangeOffset}"
                )

            if (customSegment.byteRangeLength != exoPlayerSegment.byteRangeLength)
                fail("Segment byte range length mismatch for ${exoPlayerSegment.url}")
        }
    }

    @Test
    fun testParseManifests() {
        masterManifests.forEach {
            val manifest = fetchManifest(it)
            val (customResult, exoPlayerResult) = parseMultivariantPlaylist(it, manifest)

            compareMultivariantManifest(customResult, exoPlayerResult)

            parsedMasterManifests.add(exoPlayerResult)
        }

        parsedMasterManifests.forEach { masterManifest ->
            masterManifest.variants.forEach { variant ->
                val mediaManifest = fetchManifest(variant.url.toString())

                val (customMediaResult, exoPlayerMediaResult) =
                    parseMediaPlaylist(variant.url.toString(), mediaManifest)

                compareMediaManifest(customMediaResult, exoPlayerMediaResult)

                // sleep to avoid rate limiting
                Thread.sleep(200)
            }
        }
    }

    private fun parseMediaPlaylist(
        manifestUrl: String,
        manifest: String,
    ): Pair<CustomMediaPlaylist, ExoPlayerMediaPlaylist> {
        val customResult = customParser.parse(manifestUrl, manifest)
        val exoPlayerResult = exoPlayerParser.parse(manifestUrl.toUri(), manifest.byteInputStream())

        if (customResult is CustomMediaPlaylist && exoPlayerResult is ExoPlayerMediaPlaylist)
            return Pair(customResult, exoPlayerResult)

        fail("Parsing failed")
        throw IllegalStateException("Parsing failed")
    }

    private fun parseMultivariantPlaylist(
        manifestUrl: String,
        manifest: String,
    ): Pair<CustomMultivariantPlaylist, ExoPlayerMultivariantPlaylist> {
        val customResult = customParser.parse(manifestUrl, manifest)
        val exoPlayerResult = exoPlayerParser.parse(manifestUrl.toUri(), manifest.byteInputStream())

        if (
            customResult is CustomMultivariantPlaylist &&
                exoPlayerResult is ExoPlayerMultivariantPlaylist
        )
            return Pair(customResult, exoPlayerResult)

        fail("Parsing failed")
        throw IllegalStateException("Parsing failed")
    }

    private fun compareMultivariantManifest(
        customResult: CustomMultivariantPlaylist,
        exoPlayerResult: ExoPlayerMultivariantPlaylist,
    ) {
        if (customResult.baseUri != exoPlayerResult.baseUri) fail("Base URI mismatch")

        compareVariants(customResult.variants, exoPlayerResult.variants)

        compareRenditions(customResult.videos, exoPlayerResult.videos)
        compareRenditions(customResult.audios, exoPlayerResult.audios)
        compareRenditions(customResult.subtitles, exoPlayerResult.subtitles)
        compareRenditions(customResult.closedCaptions, exoPlayerResult.closedCaptions)
    }

    private fun compareMediaManifest(
        customResult: CustomMediaPlaylist,
        exoPlayerResult: ExoPlayerMediaPlaylist,
    ) {
        if (customResult.baseUri != exoPlayerResult.baseUri) fail("Base URI mismatch")
        if (customResult.segments.size != exoPlayerResult.segments.size)
            fail("Segment count mismatch")
        if (customResult.mediaSequence != exoPlayerResult.mediaSequence)
            fail("Media sequence mismatch")
        if (customResult.hasEndTag != exoPlayerResult.hasEndTag) fail("End tag mismatch")

        compareSegments(customResult.segments, exoPlayerResult.segments)
    }
}
