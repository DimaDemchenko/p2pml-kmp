import SwiftUI

struct PlayerScreen: View {
    let videoUrl: String
    let customEngineUrl: String?

    @StateObject private var viewModel = PlayerViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var showLogShareSheet = false

    var body: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()

            if let error = viewModel.uiState.fatalError {
                VideoErrorView(errorMessage: error, onBackClick: { dismiss() })
            } else {
                PlayerContent(
                    uiState: viewModel.uiState,
                    player: viewModel.player,
                    onQualitySelected: { track in viewModel.changeTrack(track) }
                )
            }

            if let message = viewModel.uiState.userMessage {
                VStack {
                    Spacer()
                    Text(message)
                        .padding()
                        .background(Color.gray.opacity(0.9))
                        .foregroundColor(.white)
                        .cornerRadius(8)
                        .padding(.bottom, 32)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .animation(.easeInOut, value: viewModel.uiState.userMessage)
            }
        }
        .modifier(PlayerLifecycleObserver(viewModel: viewModel))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    showLogShareSheet = true
                } label: {
                    Image(systemName: "square.and.arrow.up")
                }
                .disabled(P2PFileLogSink.currentLogUrl == nil)
                .accessibilityLabel("Share P2P logs")
            }
        }
        .sheet(isPresented: $showLogShareSheet) {
            if let logUrl = P2PFileLogSink.currentLogUrl {
                ShareSheet(activityItems: [logUrl])
            }
        }
        .onAppear {
            viewModel.initializePlayer(manifestUrl: videoUrl, customEngineUrl: customEngineUrl)
        }
        .task(id: viewModel.uiState.userMessage) {
            guard viewModel.uiState.userMessage != nil else { return }
            try? await Task.sleep(for: .seconds(3))
            viewModel.onMessageConsumed()
        }
    }
}
