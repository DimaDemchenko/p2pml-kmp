import SwiftUI
import AVKit

struct PlayerContent: View {
    let uiState: PlayerUiState
    let player: AVPlayer?
    let onQualitySelected: (MediaTrack) -> Void

    @State private var showQualityDialog = false

    var body: some View {
        ZStack {
            ScrollView {
                VStack(spacing: 0) {
                    VideoPlayerSurface(
                        player: player,
                        isP2PActive: uiState.isP2PActive,
                        isVideoReady: uiState.isVideoReady,
                        onSettingsClick: { showQualityDialog = true }
                    )

                    StatsSection(
                        uiState: uiState,
                        isInitialLoading: !uiState.isVideoReady
                    )
                }
            }

            if showQualityDialog {
                Color.black.opacity(0.6).edgesIgnoringSafeArea(.all)
                    .onTapGesture { showQualityDialog = false }

                QualityDialog(
                    availableTracks: uiState.availableTracks,
                    onDismiss: { showQualityDialog = false },
                    onTrackSelected: { track in
                        onQualitySelected(track)
                        showQualityDialog = false
                    }
                )
            }
        }
    }
}
