package com.novage.p2pml.demo.data

data class MediaSample(
    val title: String,
    val uri: String,
    val description: String = "",
    val customEngineUrl: String? = null
)

object VideoStreams {
    val samples = listOf(
        MediaSample(
            "Big Buck Bunny (HLS)",
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            "Reliable HLS test stream (Mux.dev)"
        ),
        MediaSample(
            "Sintel (HLS)",
            "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
            "Multi-audio track example"
        ),
        MediaSample(
            "Bunny (Invalid P2P Engine)",
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            "Tests fallback when P2P engine is unreachable",
            customEngineUrl = "https://invalid-p2p-tracker.example.com/websocket"
        ),
        MediaSample(
            "Tears of Steel (HLS)",
            "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
            "4K High Bitrate test"
        ),
        MediaSample(
            "Apple BipBop 16x9 (Byte Range)",
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8",
            "Apple's basic multi-variant test stream (16:9 aspect ratio)."
        ),
        MediaSample(
            "Apple BipBop Gear 1 (Single Quality)",
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/gear1/prog_index.m3u8",
            "Low quality, single variant stream. Good for testing quick loading."
        ),
        MediaSample(
            "Advanced Stream (Dolby Atmos/DV)",
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/adv_dv_atmos/main.m3u8",
            "Apple test stream featuring Dolby Vision and Atmos audio."
        ),
        MediaSample(
            "Advanced HEVC (H.265)",
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8",
            "Uses the HEVC codec. Tests device decoder compatibility."
        ),
        MediaSample(
            "AWS IVS Live Stream",
            "https://fcc3ddae59ed.us-west-2.playback.live-video.net/api/video/v1/us-west-2.893648527354.channel.DmumNckWFTqz.m3u8",
            "Live broadcast simulation from Amazon IVS."
        ),
        MediaSample(
            "Bitmovin 4K Test",
            "https://cdn.bitmovin.com/content/demos/4k/38e843e0-1998-11e9-8a92-c734cd79b4dc/video_25000000.m3u8",
            "High bitrate 4K content for performance testing."
        )
    )
}
