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
            // Using weak self to prevent potential retain cycles within the closure
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
                // 1. Initialize the loader with the TWO required callbacks from your Kotlin file
                let loader = P2PMediaLoader(
                    onP2PReadyCallback: {
                        print("P2P Engine is Ready!")
                        self.isP2PReady = true
                        self.createPlayer(with: self.mediaLoader)
                        self.player?.play()
                    },
                    onP2PReadyErrorCallback: { errorMessage in
                        print("P2P Engine failed to start: \(errorMessage)")
                        // Fallback logic: Create player without P2P if engine fails
                        self.createPlayer(with: nil)
                    }
                )

                // 2. Setup event listeners (OnSegmentLoaded, OnPeerConnect, etc.)
                self.setupListeners(for: loader)

                // 3. Store reference and start the loader
                self.mediaLoader = loader
                startMediaLoader(loader)
            }
            .onDisappear {
                cleanup()
            }

            Button("Show Player Info") {
                updatePlayerInfo()
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

    // MARK: - Helper Methods

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

    private func createPlayer(with loader: P2PMediaLoader?) {
        // Use the loader to get the proxied manifest URL, or fallback to raw manifest
        let manifestUrl = loader?.getManifestUrl(manifestUrl: sampleManifest) ?? sampleManifest

        guard let url = URL(string: manifestUrl) else {
            print("Invalid manifest URL: \(manifestUrl)")
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

    private func updatePlayerInfo() {
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

    private func cleanup() {
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

    private func statusString(_ status: AVPlayerItem.Status) -> String {
        switch status {
        case .unknown: return "Unknown"
        case .readyToPlay: return "Ready"
        case .failed: return "Failed"
        @unknown default: return "Other"
        }
    }
}