import Foundation
import AVKit
import Combine
import P2PML

private let highDemandWindowSec: Int32 = 45
private let preferredBufferDurationSec = 45.0
private let simultaneousP2PDownloads: Int32 = 3
private let webRtcMaxMessageSize: Int32 = 65535
private let p2pNotReceivingBytesTimeoutMs: Int32 = 1000

@MainActor
class PlayerViewModel: ObservableObject {
    @Published var uiState = PlayerUiState()
    @Published var player: AVPlayer? = nil

    private var p2pLoader: P2PMediaLoader? = nil
    private var eventTasks: [Task<Void, Never>] = []
    private var playerItemObserver: NSKeyValueObservation?
    private var audioSelectionGroup: AVMediaSelectionGroup?
    private var shouldAutoPlay = true

    func initializePlayer(manifestUrl: String, customEngineUrl: String?) {
        guard player == nil else { return }

        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("Failed to set audio session category: \(error)")
        }

        P2PMediaLoader.companion.enableLogging()

        let coreConfig = CoreConfig()
        coreConfig.highDemandTimeWindow = highDemandWindowSec
        coreConfig.isP2PDisabled = !shouldAutoPlay
        coreConfig.simultaneousP2PDownloads = simultaneousP2PDownloads
        coreConfig.webRtcMaxMessageSize = webRtcMaxMessageSize
        coreConfig.p2pNotReceivingBytesTimeoutMs = p2pNotReceivingBytesTimeoutMs
        coreConfig.validateHTTPSegmentJs = """
            (url, byteRange, data) => {
                // console.log(`Validating segment: ${url} Range: ${byteRange}`);
                return data.byteLength > 0;
            }
            """

        let loader = P2PMediaLoader(
            coreConfig: coreConfig,
            customEngineUrl: customEngineUrl
        )

        setupP2PEvents(loader)
        self.p2pLoader = loader
        
        let newPlayer = AVPlayer()
        newPlayer.automaticallyWaitsToMinimizeStalling = true
        self.player = newPlayer

        Task { [weak self] in
            guard let self = self else { return }
            do {
                try await loader.initialize(player: newPlayer)

                let p2pUrl = try self.p2pLoader?.createPlaybackUrl(manifestUrl: manifestUrl) ?? manifestUrl
                self.startPlayback(url: p2pUrl)
                self.uiState.isP2PActive = true
            } catch let error as P2PMediaLoaderException {
                self.handleP2PError(type: error.type, errorMessage: error.message ?? "Unknown Error", originalUrl: manifestUrl)
            } catch {
                self.handleP2PError(errorMessage: error.localizedDescription, originalUrl: manifestUrl)
            }
        }
    }

    private func handleP2PError(type: P2PMediaLoaderErrorType? = nil, errorMessage: String, originalUrl: String) {
        if type == .manifestLoadError || type == .manifestParseError {
            uiState.fatalError = "Video unavailable: \(errorMessage)"
        } else {
            startPlayback(url: originalUrl)
            uiState.isP2PActive = false
            uiState.userMessage = "P2P Engine failed. Switched to HTTP mode."
        }
    }

    private func startPlayback(url: String) {
        guard let urlObj = URL(string: url) else {
            uiState.fatalError = "Invalid URL"
            return
        }

        let playerItem = AVPlayerItem(url: urlObj)
        playerItem.preferredForwardBufferDuration = preferredBufferDurationSec
        
        // Replace the item on the existing player created during initialization
        self.player?.replaceCurrentItem(with: playerItem)

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

        if shouldAutoPlay { self.player?.play() }
    }

    private func populateAvailableTracks(for playerItem: AVPlayerItem) {
        Task {
            let asset = playerItem.asset
            let currentBitrate = playerItem.preferredPeakBitRate

            var videoTracks = [MediaTrack(label: "Auto", isSelected: currentBitrate == 0, isAuto: true, bitrate: 0, isAudio: false)]

            if let urlAsset = asset as? AVURLAsset {
                let variants = try await urlAsset.load(.variants)
                var seenHeights = Set<Int>()
                let sorted = variants
                .filter { $0.videoAttributes != nil }
                .sorted { ($0.peakBitRate ?? 0) > ($1.peakBitRate ?? 0) }

                for variant in sorted {
                    guard let videoAttrs = variant.videoAttributes,
                          let peakBitRate = variant.peakBitRate,
                          peakBitRate > 0 else { continue }

                    let size = videoAttrs.presentationSize
                    let height = Int(size.height)
                    guard height > 0, seenHeights.insert(height).inserted else { continue }

                    let bitrateKbps = Int(peakBitRate / 1000)
                    let label = "\(height)p • \(bitrateKbps) kbps"

                    let isSelected = currentBitrate > 0 && Int(currentBitrate) == Int(peakBitRate)

                    videoTracks.append(MediaTrack(
                        label: label,
                        isSelected: isSelected,
                        isAuto: false,
                        bitrate: peakBitRate,
                        resolution: size,
                        isAudio: false
                    ))
                }
            }

            var audioTracks = [MediaTrack(label: "Default", isSelected: true, isAuto: true, isAudio: true)]
            if let audioGroup = try? await asset.loadMediaSelectionGroup(for: .audible) {
                self.audioSelectionGroup = audioGroup
                let selectedOption = playerItem.currentMediaSelection.selectedMediaOption(in: audioGroup)
                let options = audioGroup.options.map { option in
                    MediaTrack(
                        label: option.displayName,
                        isSelected: option == selectedOption,
                        isAuto: false,
                        isAudio: true
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

        if track.isAudio {
            if let audioGroup = audioSelectionGroup {
                if track.isAuto {
                    playerItem.select(nil, in: audioGroup)
                } else if let option = audioGroup.options.first(where: { $0.displayName == track.label }) {
                    playerItem.select(option, in: audioGroup)
                }
            }
        } else {
            if track.isAuto {
                playerItem.preferredPeakBitRate = 0
                playerItem.preferredMaximumResolution = .zero
            } else if track.bitrate > 0 {
                playerItem.preferredPeakBitRate = track.bitrate
                if let exactResolution = track.resolution {
                    playerItem.preferredMaximumResolution = exactResolution
                }
            }
        }

        populateAvailableTracks(for: playerItem)
    }

    private func setupP2PEvents(_ loader: P2PMediaLoader) {
        eventTasks.append(Task { [weak self] in
            for await details in loader.events.onChunkDownloaded {
                guard let self = self else { return }
                self.uiState.totalDownloaded += Int64(details.bytesLength)
                if details.downloadSource == .p2p {
                    self.uiState.p2pDownloaded += Int64(details.bytesLength)
                } else {
                    self.uiState.httpDownloaded += Int64(details.bytesLength)
                }
            }
        })
        eventTasks.append(Task { [weak self] in
            for await details in loader.events.onChunkUploaded {
                self?.uiState.uploadTotal += Int64(details.bytesLength)
            }
        })
        eventTasks.append(Task { [weak self] in
            for await details in loader.events.onPeerConnect {
                guard let self = self else { return }
                if !self.uiState.peers.contains(details.peerId) {
                    self.uiState.peers.append(details.peerId)
                }
            }
        })
        eventTasks.append(Task { [weak self] in
            for await details in loader.events.onPeerClose {
                self?.uiState.peers.removeAll { $0 == details.peerId }
            }
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

        let config = DynamicCoreConfig()
        config.isP2PDisabled = !enabled

        do {
            try loader.applyDynamicConfig(dynamicCoreConfig: config)
        } catch {
            print("Failed to apply dynamic config: \(error)")
        }
    }

    func releaseResources() {
        player?.pause()
        playerItemObserver?.invalidate()

        playerItemObserver = nil
        player = nil

        eventTasks.forEach { $0.cancel() }
        eventTasks.removeAll()

        p2pLoader?.release()
        
        p2pLoader = nil
    }
}
