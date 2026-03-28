import SwiftUI

struct StatsSection: View {
    let uiState: PlayerUiState
    let isInitialLoading: Bool

    let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if !uiState.isP2PActive && !isInitialLoading {

                HStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.white)
                    VStack(alignment: .leading) {
                        Text("P2P Disabled").font(.subheadline).bold()
                        Text("Engine failed. Playing via standard HTTP.").font(.caption)
                    }
                    .foregroundColor(.white)
                }
                .padding()
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(AppTheme.error)
                .cornerRadius(12)
            } else {
                Text("Live Statistics")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(AppTheme.onBackground)

                LazyVGrid(columns: columns, spacing: 8) {
                    StatCard(label: "HTTP", value: formatBytes(uiState.httpDownloaded), color: AppTheme.secondary, isLoading: isInitialLoading)
                    StatCard(label: "P2P", value: formatBytes(uiState.p2pDownloaded), color: AppTheme.primary, isLoading: isInitialLoading)
                    StatCard(label: "Upload", value: formatBytes(uiState.uploadTotal), color: AppTheme.onSurfaceVariant, isLoading: isInitialLoading)
                    StatCard(label: "Peers", value: "\(uiState.peerCount)", color: AppTheme.onSurface, isLoading: isInitialLoading)
                }
            }
        }
        .padding()
    }

    private func formatBytes(_ bytes: Int64) -> String {
        if bytes == 0 { return "0 MB" }
        let formatter = ByteCountFormatter()
        formatter.allowedUnits = [.useMB, .useGB]
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}
