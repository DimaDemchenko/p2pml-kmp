import SwiftUI
import AVKit

struct VideoPlayerSurface: View {
    let player: AVPlayer?
    let isP2PActive: Bool
    let isVideoReady: Bool
    let onSettingsClick: () -> Void

    var body: some View {
        ZStack {
            Color.black.aspectRatio(16/9, contentMode: .fit)

            if let player = player {
                VideoPlayer(player: player)
                    .aspectRatio(16/9, contentMode: .fit)
            }

            if !isVideoReady {
                VStack(spacing: 8) {
                    ProgressView().tint(AppTheme.primary)
                    Text("Loading Stream...")
                        .font(.caption)
                        .foregroundColor(AppTheme.onSurfaceVariant)
                }
            }

            VStack {
                HStack(alignment: .top) {
                    if isVideoReady {
                        Text(isP2PActive ? "P2P ON" : "P2P OFF")
                            .font(.caption2)
                            .fontWeight(.bold)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 4)
                            .background(isP2PActive ? AppTheme.primary.opacity(0.8) : AppTheme.error.opacity(0.8))
                            .foregroundColor(isP2PActive ? AppTheme.onPrimary : AppTheme.onError)
                            .cornerRadius(4)
                            .padding(8)
                    }
                    Spacer()
                    Button(action: onSettingsClick) {
                        Image(systemName: "gearshape.fill")
                            .foregroundColor(.white)
                            .padding(8)
                    }
                }
                Spacer()
            }
        }
        .aspectRatio(16/9, contentMode: .fit)
    }
}
