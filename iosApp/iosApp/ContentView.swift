import SwiftUI
import WebKit
import Shared
import AVKit
import Combine

struct ContentView: View {
    @State private var player: AVPlayer? = nil
    @State private var playerInfo: String = ""
    @State private var mediaLoader: P2PMediaLoader? = nil
    @State private var statusObserver: AnyCancellable? = nil
    @State private var isP2PReady = false

    @State private var eventSubscriptions: [Shared.Cancellable] = []

    private let sampleManifest =
        "https://cdn.bitmovin.com/content/demos/4k/38e843e0-1998-11e9-8a92-c734cd79b4dc/video_25000000.m3u8"

    private func startMediaLoader(_ loader: P2PMediaLoader) {
        loader.start(getPlaybackInfo: {
            guard let validPlayer = self.player else {
                return PlaybackInfo(currentPlayPosition: 0.0, currentPlaybackSpeed: 0.0)
            }
            let currentSeconds = CMTimeGetSeconds(validPlayer.currentTime())
            let rate = validPlayer.rate
            return PlaybackInfo(
                currentPlayPosition: currentSeconds,
                currentPlaybackSpeed: Float(rate)
            )
        })
    }

    var body: some View {
        VStack(spacing: 20) {
            VideoPlayer(player: player)
            .frame(height: 300)
            .onAppear {
                var loader: P2PMediaLoader! = nil

                loader = P2PMediaLoader(onP2PReadyCallback: {
                    self.isP2PReady = true

                    self.createPlayer(with: loader)
                    self.player?.play()
                })

                self.setupListeners(for: loader)

                startMediaLoader(loader)
                self.mediaLoader = loader
            }
            .onDisappear {
                eventSubscriptions.forEach {
                    $0.cancel()
                }
                eventSubscriptions.removeAll()

                player?.pause()
                player = nil
                statusObserver?.cancel()
                statusObserver = nil
                mediaLoader = nil
                isP2PReady = false
            }

            Button("Show Player Info") {
                if let player = player {
                    let currentSeconds = CMTimeGetSeconds(player.currentTime())
                    let speed = player.rate
                    let status = player.currentItem?.status ?? .unknown
                    playerInfo = String(
                        format: "Time: %.2f sec, Speed: %.2f, Status: %@",
                        currentSeconds, speed, statusString(status)
                    )
                } else {
                    playerInfo = "Player is not available."
                }
            }
            .padding()
            .background(Color.blue.opacity(0.8))
            .foregroundColor(.white)
            .cornerRadius(8)

            Text(playerInfo)
                .padding()
                .multilineTextAlignment(.center)

            if !isP2PReady {
                Text("⏳ Initializing P2P...")
                    .foregroundColor(.gray)
                    .font(.caption)
            }
        }
        .padding()
    }

    private func setupListeners(for loader: P2PMediaLoader) {
        eventSubscriptions.append(loader.observeSegmentLoaded { details in
            print("Segment loaded: \(details.bytesLength) bytes from \(details.downloadSource)")
        })

        eventSubscriptions.append(loader.observePeerConnect { details in
            print("Peer Connected: \(details.peerId)")
        })

        eventSubscriptions.append(loader.observeSegmentError { details in
            print("Segment Error: \(details.error)")
        })

        eventSubscriptions.append(loader.observeChunkDownloaded { details in
            if let peerId = details.peerId {
                print("Chunk from peer \(peerId): \(details.bytesLength) bytes")
            } else {
                print("Chunk from CDN: \(details.bytesLength) bytes")
            }
        })
    }

    private func createPlayer(with loader: P2PMediaLoader) {
        let manifestUrl = loader.getManifestUrl(manifestUrl: sampleManifest)

        guard let url = URL(string: manifestUrl) else {
            print("Invalid manifest URL from loader: \(manifestUrl)")
            return
        }

        let newPlayer = AVPlayer(url: url)
        self.player = newPlayer

        NotificationCenter.default.addObserver(
            forName: .AVPlayerItemFailedToPlayToEndTime,
            object: newPlayer.currentItem,
            queue: .main
        ) { notification in
            if let error = notification.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error {
                print("Playback failed: \(error.localizedDescription)")
            }
        }
    }

    private func statusString(_ status: AVPlayerItem.Status) -> String {
        switch status {
        case .unknown: return "Unknown"
        case .readyToPlay: return "Ready"
        case .failed: return "Failed"
        @unknown default: return "Other"
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}