package com.novage.p2pml.demo.data

data class VideoStream(val title: String, val uri: String, val description: String = "")

object VideoStreams {
    val samples = listOf(
        VideoStream(
            "Big Buck Bunny (HLS)",
            "https://test-streams.mux.dev/x36xhzz/url_0/193039199_mp4_h264_aac_hd_7.m3u8",
            "Reliable HLS test stream"
        ),
        VideoStream(
            "Sintel (HLS)",
            "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
            "Multi-audio track example"
        ),
        VideoStream(
            "Tears of Steel (HLS)",
            "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
            "4K High Bitrate test"
        ),
        VideoStream(
            title = "Tears of Steel 4K (HLS)",
            "https://cdn.bitmovin.com/content/demos/4k/38e843e0-1998-11e9-8a92-c734cd79b4dc/video_25000000.m3u8",
            "4K High Bitrate test"
        )
    )
}
