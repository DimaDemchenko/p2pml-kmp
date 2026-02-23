import SwiftUI
import AVKit

struct PlayerView: View {
    let videoUrl: String
    let customEngineUrl: String?

    @StateObject private var viewModel = PlayerViewModel()

    var body: some View {
        VStack(spacing: 0) {
            if let player = viewModel.player {
                VideoPlayer(player: player)
                    .frame(height: 300)
                    .background(Color.black)
            } else {
                Rectangle()
                    .fill(Color.black)
                    .frame(height: 300)
                    .overlay(ProgressView().tint(.white))
            }

            VStack(alignment: .leading, spacing: 12) {
                Text("Live Statistics").font(.title2).bold()
                Text("HTTP Downloaded: \(formatBytes(viewModel.uiState.httpDownloaded))")
                Text("P2P Downloaded: \(formatBytes(viewModel.uiState.p2pDownloaded))")
                Text("Upload Total: \(formatBytes(viewModel.uiState.uploadTotal))")
                Text("Peers: \(viewModel.uiState.peerCount)")
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)

            Spacer()
        }
        .navigationTitle("Player")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            viewModel.initializePlayer(manifestUrl: videoUrl, customEngineUrl: customEngineUrl)
        }
        .onDisappear {
            viewModel.releaseResources()
        }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.allowedUnits = [.useMB, .useGB]
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}
