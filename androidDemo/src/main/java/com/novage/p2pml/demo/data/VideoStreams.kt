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
            "Reliable HLS test stream"
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
            "Tears of Steel 4K (HLS)",
            "https://cdn.bitmovin.com/content/demos/4k/38e843e0-1998-11e9-8a92-c734cd79b4dc/video_25000000.m3u8",
            "4K High Bitrate test"
        )
    )
}
