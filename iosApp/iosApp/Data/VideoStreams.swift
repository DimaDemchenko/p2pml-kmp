import Foundation

struct MediaSample: Hashable {
    let title: String
    let uri: String
    let description: String
    var customEngineUrl: String? = nil
}

struct VideoStreams {
    static let samples: [MediaSample] = [
        MediaSample(
            title: "Big Buck Bunny (HLS)",
            uri: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            description: "Reliable HLS test stream (Mux.dev)"
        ),
        MediaSample(
            title: "Sintel (HLS)",
            uri: "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
            description: "Multi-audio track example"
        ),
        MediaSample(
            title: "Bunny (Invalid P2P Engine)",
            uri: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            description: "Tests fallback when P2P engine is unreachable",
            customEngineUrl: "https://invalid-p2p-engine.example.com/"
        ),
        MediaSample(
            title: "Tears of Steel (HLS)",
            uri: "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
            description: "4K High Bitrate test"
        ),
        MediaSample(
            title: "Apple BipBop 16x9 (Byte Range)",
            uri: "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8",
            description: "Apple's basic multi-variant test stream (16:9 aspect ratio)."
        ),
        MediaSample(
            title: "Apple BipBop Gear 1 (Single Quality)",
            uri: "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/gear1/prog_index.m3u8",
            description: "Low quality, single variant stream. Good for testing quick loading."
        ),
        MediaSample(
            title: "Advanced Stream (Dolby Atmos/DV)",
            uri: "https://devstreaming-cdn.apple.com/videos/streaming/examples/adv_dv_atmos/main.m3u8",
            description: "Apple test stream featuring Dolby Vision and Atmos audio."
        ),
        MediaSample(
            title: "Advanced HEVC (H.265)",
            uri: "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8",
            description: "Uses the HEVC codec. Tests device decoder compatibility."
        ),
        MediaSample(
            title: "AWS IVS Live Stream",
            uri: "https://fcc3ddae59ed.us-west-2.playback.live-video.net/api/video/v1/us-west-2.893648527354.channel.DmumNckWFTqz.m3u8",
            description: "Live broadcast simulation from Amazon IVS."
        ),
        MediaSample(
            title: "Bitmovin 4K Test",
            uri: "https://cdn.bitmovin.com/content/demos/4k/38e843e0-1998-11e9-8a92-c734cd79b4dc/video_25000000.m3u8",
            description: "High bitrate 4K content for performance testing."
        )
    ]
}
