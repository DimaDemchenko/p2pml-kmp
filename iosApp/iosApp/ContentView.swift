import SwiftUI
import WebKit
import Shared
import AVKit

struct ContentView: View {
    @State private var showContent = false
    @State private var webView: WKWebView = WKWebView(frame: .zero)
    @State private var player: AVPlayer? = nil
    @State private var playerInfo: String = ""

    private var mediaLoader: P2PMediaLoader

    init() {
         let wkWebView = WKWebView(frame: .zero)
         if #available(iOS 16.4, *) {
             wkWebView.isInspectable = true
         }
         _webView = State(initialValue: wkWebView)
         self.mediaLoader = P2PMediaLoader(platformWebView: PlatformWebView(webView: wkWebView))
         self.mediaLoader.start()
    }

    var body: some View {
        VStack(spacing: 20) {
            VideoPlayer(player: player)
                .frame(height: 300)
                .onAppear {
                    // Replace the URL string with your actual HLS stream URL.
                    // let manifestUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                    let manifest = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/gear1/prog_index.m3u8"
                    let manifestUrl = mediaLoader.getManifestUrl(manifestUrl: manifest)
                    //let manifestUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"
                    if let url = URL(string: manifestUrl) {
                        player = AVPlayer(url: url)
                        player?.play()
                    }
                }
                .onDisappear {
                    player?.pause()
                    player = nil
                }

            // Button to display current play position and playback speed.
            Button("Show Player Info") {
                if let player = player {
                    // Get current time in seconds.
                    let currentSeconds = CMTimeGetSeconds(player.currentTime())
                    // Get current playback speed.
                    let speed = player.rate
                    playerInfo = String(format: "Current time: %.2f seconds, Speed: %.2f", currentSeconds, speed)
                } else {
                    playerInfo = "Player is not available."
                }
            }
            .padding()
            .background(Color.blue.opacity(0.8))
            .foregroundColor(.white)
            .cornerRadius(8)

            // Display the player information.
            Text(playerInfo)
                .padding()
                .multilineTextAlignment(.center)
        }
        .padding()
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
