import Foundation
import AVKit
import Combine
import P2PML

@MainActor
class PlayerViewModel: ObservableObject {
    @Published var uiState = PlayerUiState()
    @Published var player: AVPlayer? = nil

    private var p2pLoader: P2PMediaLoader? = nil
    private var eventSubscriptions: [P2PML.Cancellable] = []
    private var playerItemObserver: Any?
    private var shouldAutoPlay = true

    func initializePlayer(manifestUrl: String, customEngineUrl: String?) {
        guard player == nil else { return }

        P2PMediaLoader.companion.enableLogging()

        // Exact config mapping from Android
        let coreConfig = """
                         {
                             "highDemandTimeWindow": 20,
                             "isP2PDisabled": \(!shouldAutoPlay),
                             "simultaneousP2PDownloads": 3,
                             "webRtcMaxMessageSize": 65535,
                             "p2pNotReceivingBytesTimeoutMs": 1000
                         }
                         """.replacingOccurrences(of: "\n", with: " ")

        let loader = P2PMediaLoader(
            onReady: { [weak self] in
                guard let self = self else { return }
                let p2pUrl = self.p2pLoader?.getManifestUrl(manifestUrl: manifestUrl) ?? manifestUrl
                self.startPlayback(url: p2pUrl)
                self.uiState.isP2PActive = true
            },
            onError: { [weak self] errorType, errorMessage in
                self?.handleP2PError(errorMessage: errorMessage, originalUrl: manifestUrl)
            },
            coreConfig: coreConfig,
            customEngineUrl: customEngineUrl
        )

        setupP2PEvents(loader)

        loader.start(getPlaybackInfo: { [weak self] in
            guard let validPlayer = self?.player else {
                return PlaybackInfo(currentPlayPosition: 0.0, currentPlaybackSpeed: 0.0)
            }
            let currentSeconds = CMTimeGetSeconds(validPlayer.currentTime())
            return PlaybackInfo(
                currentPlayPosition: currentSeconds.isNaN ? 0.0 : currentSeconds,
                currentPlaybackSpeed: Float(validPlayer.rate)
            )
        })

        self.p2pLoader = loader
    }

    private func handleP2PError(errorMessage: String, originalUrl: String) {
        startPlayback(url: originalUrl)
        uiState.isP2PActive = false
        uiState.userMessage = "P2P Engine failed. Switched to HTTP mode."
    }

    private func startPlayback(url: String) {
        guard let urlObj = URL(string: url) else {
            uiState.fatalError = "Invalid URL"
            return
        }

        let playerItem = AVPlayerItem(url: urlObj)
        let newPlayer = AVPlayer(playerItem: playerItem)
        newPlayer.automaticallyWaitsToMinimizeStalling = true // iOS Default LoadControl
        self.player = newPlayer

        // Monitor buffering state
        playerItemObserver = playerItem.observe(\.status, options: [.new]) { [weak self] item, _ in
            DispatchQueue.main.async {
                if item.status == .readyToPlay {
                    self?.uiState.isVideoReady = true
                    // Mock tracks to match Android UI (AVPlayer handles this natively)
                    self?.uiState.availableTracks = AvailableTracks(
                        videoTracks: [MediaTrack(label: "Auto", isSelected: true, isAuto: true)],
                        audioTracks: [MediaTrack(label: "Default", isSelected: true, isAuto: true)]
                    )
                } else if item.status == .failed {
                    self?.uiState.fatalError = "Video unavailable: \(item.error?.localizedDescription ?? "Unknown error")"
                }
            }
        }

        if shouldAutoPlay { newPlayer.play() }
    }

    private func setupP2PEvents(_ loader: P2PMediaLoader) {
        eventSubscriptions.append(loader.onChunkDownloaded { [weak self] details in
            guard let self = self else { return }
            self.uiState.totalDownloaded += Int64(details.bytesLength)
            if details.downloadSource == "p2p" {
                self.uiState.p2pDownloaded += Int64(details.bytesLength)
            } else {
                self.uiState.httpDownloaded += Int64(details.bytesLength)
            }
        })
        eventSubscriptions.append(loader.onChunkUploaded { [weak self] details in
            self?.uiState.uploadTotal += Int64(details.bytesLength)
        })
        eventSubscriptions.append(loader.onPeerConnect { [weak self] _ in
            self?.uiState.peerCount += 1
        })
        eventSubscriptions.append(loader.onPeerClose { [weak self] _ in
            self?.uiState.peerCount = max(0, (self?.uiState.peerCount ?? 1) - 1)
        })
    }

    func onMessageConsumed() { uiState.userMessage = nil }

    func changeTrack(_ track: MediaTrack) {
        // AVPlayer handles HLS variants automatically.
        // Deep integration via AVAssetReader is needed to override this manually in iOS.
    }

    func play() {
        shouldAutoPlay = true
        player?.play()
    }

    func pause() {
        shouldAutoPlay = false
        player?.pause()
    }

    func releaseResources() {
        player?.pause()
        player = nil
        eventSubscriptions.forEach { $0.cancel() }
        eventSubscriptions.removeAll()
        p2pLoader?.release()
        p2pLoader = nil
    }
}
