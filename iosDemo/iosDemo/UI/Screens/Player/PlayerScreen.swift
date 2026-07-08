import SwiftUI

struct PlayerScreen: View {
    let videoUrl: String
    let customEngineUrl: String?

    @StateObject private var viewModel = PlayerViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var showSnackbar = false
    @State private var showLogShareSheet = false

    var body: some View {
        ZStack {
            AppTheme.background.edgesIgnoringSafeArea(.all)

            if let error = viewModel.uiState.fatalError {
                VideoErrorView(errorMessage: error, onBackClick: { dismiss() })
            } else {
                PlayerContent(
                    uiState: viewModel.uiState,
                    player: viewModel.player,
                    onQualitySelected: { track in viewModel.changeTrack(track) }
                )
            }

            if showSnackbar, let message = viewModel.uiState.userMessage {
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
                .animation(.easeInOut, value: showSnackbar)
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
                .disabled(P2PFileLogSink.currentLogURL == nil)
                .accessibilityLabel("Share P2P logs")
            }
        }
        .sheet(isPresented: $showLogShareSheet) {
            if let logURL = P2PFileLogSink.currentLogURL {
                ShareSheet(activityItems: [logURL])
            }
        }
        .onAppear {
            viewModel.initializePlayer(manifestUrl: videoUrl, customEngineUrl: customEngineUrl)
        }
        .onChange(of: viewModel.uiState.userMessage) { newValue in
            if newValue != nil {
                showSnackbar = true
            }
        }
        .task(id: showSnackbar) {
            guard showSnackbar else { return }
            try? await Task.sleep(for: .seconds(3))
            showSnackbar = false
            viewModel.onMessageConsumed()
        }
    }
}
