import SwiftUI

struct PlayerScreen: View {
    let videoUrl: String
    let customEngineUrl: String?

    @StateObject private var viewModel = PlayerViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var showSnackbar = false

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
                        .onAppear {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                                showSnackbar = false
                                viewModel.onMessageConsumed()
                            }
                        }
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .animation(.easeInOut, value: showSnackbar)
            }
        }
        .modifier(PlayerLifecycleObserver(viewModel: viewModel))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            viewModel.initializePlayer(manifestUrl: videoUrl, customEngineUrl: customEngineUrl)
        }
        .onDisappear {
            viewModel.releaseResources()
        }
        .onChange(of: viewModel.uiState.userMessage) { msg in
            if msg != nil { showSnackbar = true }
        }
    }
}
