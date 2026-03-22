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
    private var playerItemObserver: NSKeyValueObservation?
    private var audioSelectionGroup: AVMediaSelectionGroup?
    private var shouldAutoPlay = true

    func initializePlayer(manifestUrl: String, customEngineUrl: String?) {
        guard player == nil else { return }

        P2PMediaLoader.companion.enableLogging()

        let coreConfig = CoreConfig(
            highDemandTimeWindow: 20,
            isP2PDisabled: !shouldAutoPlay,
            simultaneousP2PDownloads: 3,
            webRtcMaxMessageSize: 65535,
            p2pNotReceivingBytesTimeoutMs: 1000
        )

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
        newPlayer.automaticallyWaitsToMinimizeStalling = true
        self.player = newPlayer

        playerItemObserver = playerItem.observe(\.status, options: [.new]) { [weak self] item, _ in
            DispatchQueue.main.async {
                if item.status == .readyToPlay {
                    self?.uiState.isVideoReady = true
                    self?.populateAvailableTracks(for: item)
                } else if item.status == .failed {
                    self?.uiState.fatalError = "Video unavailable: \(item.error?.localizedDescription ?? "Unknown error")"
                }
            }
        }

        if shouldAutoPlay { newPlayer.play() }
    }

    private func populateAvailableTracks(for playerItem: AVPlayerItem) {
        Task {
            let asset = playerItem.asset
            let currentBitrate = playerItem.preferredPeakBitRate

            var videoTracks = [MediaTrack(label: "Auto", isSelected: currentBitrate == 0, isAuto: true, bitrate: 0)]

            if let urlAsset = asset as? AVURLAsset {
                let variants = (try? await urlAsset.load(.variants)) ?? urlAsset.variants
                var seenHeights = Set<Int>()
                let sorted = variants
                    .filter { $0.videoAttributes != nil }
                    .sorted { ($0.peakBitRate ?? 0) > ($1.peakBitRate ?? 0) }

                for variant in sorted {
                    guard let videoAttrs = variant.videoAttributes,
                          let peakBitRate = variant.peakBitRate,
                          peakBitRate > 0 else { continue }
                    let height = Int(videoAttrs.presentationSize.height)
                    guard height > 0, seenHeights.insert(height).inserted else { continue }

                    let bitrateKbps = Int(peakBitRate / 1000)
                    let label = "\(height)p • \(bitrateKbps) kbps"
                    let isSelected = currentBitrate > 0 && currentBitrate == peakBitRate
                    videoTracks.append(MediaTrack(
                        label: label,
                        isSelected: isSelected,
                        isAuto: false,
                        bitrate: peakBitRate
                    ))
                }
            }

            var audioTracks = [MediaTrack(label: "Default", isSelected: true, isAuto: true)]
            if let audioGroup = try? await asset.loadMediaSelectionGroup(for: .audible) {
                self.audioSelectionGroup = audioGroup
                let selectedOption = playerItem.currentMediaSelection.selectedMediaOption(in: audioGroup)
                let options = audioGroup.options.map { option in
                    MediaTrack(
                        label: option.displayName,
                        isSelected: option == selectedOption,
                        isAuto: false
                    )
                }
                if !options.isEmpty {
                    audioTracks = options
                }
            }

            uiState.availableTracks = AvailableTracks(
                videoTracks: videoTracks,
                audioTracks: audioTracks
            )
        }
    }

    func changeTrack(_ track: MediaTrack) {
        guard let playerItem = player?.currentItem else { return }

        if track.isAuto {
            playerItem.preferredPeakBitRate = 0
        } else if track.bitrate > 0 {
            playerItem.preferredPeakBitRate = track.bitrate
        } else if let audioGroup = audioSelectionGroup,
                  let option = audioGroup.options.first(where: { $0.displayName == track.label }) {
            playerItem.select(option, in: audioGroup)
        }

        populateAvailableTracks(for: playerItem)
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

    func play() {
        shouldAutoPlay = true
        player?.play()
        applyP2PEnabled(true)
    }

    func pause() {
        shouldAutoPlay = false
        player?.pause()
        applyP2PEnabled(false)
    }

    private func applyP2PEnabled(_ enabled: Bool) {
        guard let loader = p2pLoader else { return }

        let config = DynamicCoreConfig(
            isP2PDisabled: !enabled
        )

        loader.applyDynamicConfig(dynamicCoreConfig: config)
    }

    func releaseResources() {
        player?.pause()
        playerItemObserver?.invalidate()
        playerItemObserver = nil
        player = nil
        eventSubscriptions.forEach { $0.cancel() }
        eventSubscriptions.removeAll()
        p2pLoader?.release()
        p2pLoader = nil
    }
}
