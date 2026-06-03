import SwiftUI

struct QualityDialog: View {
    let availableTracks: AvailableTracks
    let onDismiss: () -> Void
    let onTrackSelected: (MediaTrack) -> Void

    var body: some View {
        VStack(alignment: .leading) {
            Text("Playback Settings")
                .font(.headline)
                .foregroundColor(AppTheme.onSurface)
                .padding()

            Divider().background(AppTheme.onSurfaceVariant)

            ScrollView {
                VStack(alignment: .leading) {
                    if !availableTracks.videoTracks.isEmpty {
                        Text("Video Quality")
                            .font(.subheadline)
                            .foregroundColor(AppTheme.primary)
                            .padding(.horizontal).padding(.top, 8)

                        ForEach(availableTracks.videoTracks) { track in
                            trackRow(track)
                        }
                    }

                    if availableTracks.audioTracks.count > 1 {
                        Text("Audio Track")
                            .font(.subheadline)
                            .foregroundColor(AppTheme.primary)
                            .padding(.horizontal).padding(.top, 8)

                        ForEach(availableTracks.audioTracks) { track in
                            trackRow(track)
                        }
                    }
                }
            }

            HStack {
                Spacer()
                Button("Close", action: onDismiss)
                    .foregroundColor(AppTheme.primary)
                    .padding()
            }
        }
        .background(AppTheme.surface)
        .cornerRadius(12)
        .padding(32)
    }

    @ViewBuilder
    private func trackRow(_ track: MediaTrack) -> some View {
        Button(action: { onTrackSelected(track) }) {
            HStack {
                Image(systemName: track.isSelected ? "largecircle.fill.circle" : "circle")
                    .foregroundColor(track.isSelected ? AppTheme.primary : AppTheme.onSurfaceVariant)
                Text(track.label)
                    .foregroundColor(track.isSelected ? AppTheme.primary : AppTheme.onSurface)
                    .fontWeight(track.isSelected ? .bold : .regular)
                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.horizontal)
        }
    }
}
