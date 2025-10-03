import SwiftUI
import WebKit
import Shared
import AVKit

struct ContentView: View {
    @State private var webView: WKWebView
    @State private var player: AVPlayer? = nil
    @State private var playerInfo: String = ""

    private var mediaLoader: P2PMediaLoader

    init() {
        let wkWebView = WKWebView(frame: .zero)
        if #available(iOS 16.4, *) {
            wkWebView.isInspectable = true
        }
        // Store the WKWebView in our @State property
        _webView = State(initialValue: wkWebView)
        


        // Create our P2PMediaLoader using the shared KMP module
        self.mediaLoader = P2PMediaLoader(
            platformWebView: PlatformWebView(webView: wkWebView)
        )
    }

    func startMediaLoader() {
            // 3) Start the loader; pass a closure that returns PlaybackInfo
            mediaLoader.start(
                getPlaybackInfo: {
                    // If there's no player yet, return defaults
                    guard let validPlayer = self.player else {
                        return PlaybackInfo(
                            currentPlayPosition: 0.0,
                            currentPlaybackSpeed: 0.0
                        )
                    }
                    let currentSeconds = CMTimeGetSeconds(validPlayer.currentTime())
                    let rate = validPlayer.rate

                    return PlaybackInfo(
                        currentPlayPosition: currentSeconds,
                        currentPlaybackSpeed: Float(rate)
                    )
                }
            )
        }

    var body: some View {
        VStack(spacing: 20) {
            // Show the video
            VideoPlayer(player: player)
                .frame(height: 300)
                .onAppear {

                    // 2) Setup a sample HLS stream (BipBop) and start playback
                    let manifest = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                    let manifestUrl = self.mediaLoader.getManifestUrl(manifestUrl: manifest)
                    // wait 6 seconds before starting the player
                    DispatchQueue.main.asyncAfter(deadline: .now() + 10) {
                            if let url = URL(string: manifestUrl) {
                                let newPlayer = AVPlayer(url: url)
                                self.startMediaLoader()
                                newPlayer.play()
                                self.player = newPlayer
                            }
                        }
                }
                .onDisappear {
                    // Pause and clear player when the view disappears
                    player?.pause()
                    player = nil
                }

            // Button to display current play position and speed
            Button("Show Player Info") {
                if let player = player {
                    let currentSeconds = CMTimeGetSeconds(player.currentTime())
                    let speed = player.rate
                    self.playerInfo = String(
                        format: "Current time: %.2f seconds, Speed: %.2f",
                        currentSeconds, speed
                    )
                } else {
                    self.playerInfo = "Player is not available."
                }
            }
            .padding()
            .background(Color.blue.opacity(0.8))
            .foregroundColor(.white)
            .cornerRadius(8)

            // Display the player information
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
