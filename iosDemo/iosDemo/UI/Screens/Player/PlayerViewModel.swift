import AVKit
import Combine
import Foundation
import os
import P2PML

private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "com.novage.p2pml", category: "PlayerViewModel")

private let highDemandWindowSec: Int32 = 45
private let preferredBufferDurationSec = 45.0
private let simultaneousP2PDownloads: Int32 = 3
private let webRtcMaxMessageSize: Int32 = 65535
private let p2pNotReceivingBytesTimeoutMs: Int32 = 1000

@MainActor
class PlayerViewModel: ObservableObject {
    @Published var uiState = PlayerUiState()
    @Published var player: AVPlayer?

    private var p2pLoader: P2PMediaLoader?
    private var populateTracksTask: Task<Void, Never>?
    private var playerItemObserver: NSKeyValueObservation?
    private var audioSelectionGroup: AVMediaSelectionGroup?
    private var shouldAutoPlay = true
    private var originalManifestUrl: String?
    private var hasFallenBackToHttp = false

    func initializePlayer(manifestUrl: String, customEngineUrl: String?) {
        guard player == nil else { return }

        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            logger.warning("Failed to set audio session category: \(error.localizedDescription)")
        }

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

        originalManifestUrl = manifestUrl
        setupP2PEvents(loader)
        observeLoaderState(loader)
        p2pLoader = loader

        let newPlayer = AVPlayer()
        newPlayer.automaticallyWaitsToMinimizeStalling = true
        player = newPlayer

        Task { [weak self] in
            guard let self else { return }
            do {
                try await loader.initialize(player: newPlayer)

                let p2pUrl = try p2pLoader?.createPlaybackUrl(manifestUrl: manifestUrl) ?? manifestUrl
                startPlayback(url: p2pUrl)
                uiState.isP2PActive = true
            } catch let error as P2PMediaLoaderException {
                self.fallBackToHttp(reason: "initialize threw \(error.code): \(error.message)")
            } catch {
                fallBackToHttp(reason: error.localizedDescription)
            }
        }
    }

    private func observeLoaderState(_ loader: P2PMediaLoader) {
        Task { [weak self] in
            for await state in loader.state {
                guard let self else { return }
                if state.status == .failed {
                    fallBackToHttp(reason: "state=FAILED code=\(String(describing: state.error?.code))")
                }

                if state.status == .failed || state.status == .released {
                    break
                }
            }
        }
    }

    private func fallBackToHttp(reason: String) {
        guard !hasFallenBackToHttp, let originUrl = originalManifestUrl else { return }

        hasFallenBackToHttp = true

        logger.warning("Falling back to HTTP playback: \(reason)")
        startPlayback(url: originUrl)
        uiState.isP2PActive = false
        uiState.userMessage = "P2P Engine failed. Switched to HTTP mode."
    }

    private func startPlayback(url: String) {
        guard let urlObj = URL(string: url) else {
            uiState.fatalError = "Invalid URL"
            return
        }

        let playerItem = AVPlayerItem(url: urlObj)
        playerItem.preferredForwardBufferDuration = preferredBufferDurationSec

        // Replace the item on the existing player created during initialization
        player?.replaceCurrentItem(with: playerItem)

        playerItemObserver = playerItem.observe(\.status, options: [.new]) { [weak self] item, _ in
            DispatchQueue.main.async {
                if item.status == .readyToPlay {
                    self?.uiState.isVideoReady = true
                    self?.populateAvailableTracks(for: item)
                } else if item.status == .failed {
                    let reason = item.error?.localizedDescription ?? "Unknown error"
                    self?.uiState.fatalError = "Video unavailable: \(reason)"
                }
            }
        }

        if shouldAutoPlay {
            player?.play()
        }
    }

    private func populateAvailableTracks(for playerItem: AVPlayerItem) {
        populateTracksTask?.cancel()
        populateTracksTask = Task {
            let videoTracks = await loadVideoTracks(for: playerItem)

            var audioTracks = [MediaTrack(label: "Default", isSelected: true, isAuto: true, isAudio: true)]
            if let audioGroup = try? await playerItem.asset.loadMediaSelectionGroup(for: .audible) {
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

            guard !Task.isCancelled else { return }

            uiState.availableTracks = AvailableTracks(
                videoTracks: videoTracks,
                audioTracks: audioTracks
            )
        }
    }

    private func loadVideoTracks(for playerItem: AVPlayerItem) async -> [MediaTrack] {
        let currentBitrate = playerItem.preferredPeakBitRate

        var videoTracks = [MediaTrack(
            label: "Auto",
            isSelected: currentBitrate == 0,
            isAuto: true,
            bitrate: 0,
            isAudio: false
        )]

        guard let urlAsset = playerItem.asset as? AVURLAsset else { return videoTracks }

        do {
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
        } catch {
            logger.warning("Failed to load HLS variants: \(error.localizedDescription)")
        }

        return videoTracks
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
        // Event streams complete when the loader reaches a terminal state, so these loops end
        // on their own after release() — no cancellation bookkeeping needed.
        Task { [weak self] in
            for await details in loader.p2pEvents.onChunkDownloaded {
                guard let self else { return }
                uiState.totalDownloaded += Int64(details.bytesLength)
                if details.downloadSource == .p2p {
                    uiState.p2pDownloaded += Int64(details.bytesLength)
                } else {
                    uiState.httpDownloaded += Int64(details.bytesLength)
                }
            }
        }
        Task { [weak self] in
            for await details in loader.p2pEvents.onChunkUploaded {
                self?.uiState.uploadTotal += Int64(details.bytesLength)
            }
        }
        Task { [weak self] in
            for await details in loader.p2pEvents.onPeerConnect {
                guard let self else { return }
                if !uiState.peers.contains(details.peerId) {
                    uiState.peers.append(details.peerId)
                }
            }
        }
        Task { [weak self] in
            for await details in loader.p2pEvents.onPeerClose {
                self?.uiState.peers.removeAll { $0 == details.peerId }
            }
        }
    }

    func onMessageConsumed() {
        uiState.userMessage = nil
    }

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

        loader.applyDynamicConfig(dynamicCoreConfig: config)
    }

    func releaseResources() {
        player?.pause()
        playerItemObserver?.invalidate()

        playerItemObserver = nil
        player = nil

        populateTracksTask?.cancel()
        populateTracksTask = nil

        p2pLoader?.release()

        p2pLoader = nil

        originalManifestUrl = nil
        hasFallenBackToHttp = false
    }

    deinit {
        MainActor.assumeIsolated {
            releaseResources()
        }
    }
}
