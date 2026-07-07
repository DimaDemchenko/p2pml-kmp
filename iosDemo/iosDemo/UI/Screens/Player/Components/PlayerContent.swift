import AVKit
import SwiftUI

struct PlayerContent: View {
    let uiState: PlayerUiState
    let player: AVPlayer?
    let onQualitySelected: (MediaTrack) -> Void

    @State private var showQualityDialog = false

    var body: some View {
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
        .sheet(isPresented: $showQualityDialog) {
            QualityDialog(
                availableTracks: uiState.availableTracks,
                onDismiss: { showQualityDialog = false },
                onTrackSelected: { track in
                    onQualitySelected(track)
                    showQualityDialog = false
                }
            )
            .presentationDetents([.medium])
            .presentationDragIndicator(.visible)
        }
    }
}
