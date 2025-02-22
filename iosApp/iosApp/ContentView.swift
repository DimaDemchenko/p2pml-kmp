import SwiftUI
import WebKit
import Shared
import AVKit

struct ContentView: View {
    @State private var showContent = false
    @State private var webView: WKWebView = WKWebView(frame: .zero)
    @State private var player: AVPlayer? = nil

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
                    let manifestUrl = self.mediaLoader.getManifestUrl(manifestUrl: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    if let url = URL(string: manifestUrl) {
                        player = AVPlayer(url: url)
                        player?.play()
                    }
                }
                .onDisappear {
                    player?.pause()
                    player = nil
                }
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
