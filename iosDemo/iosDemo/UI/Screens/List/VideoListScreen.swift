import SwiftUI

struct VideoListScreen: View {
    @State private var customUrl: String = ""

    var isUrlValid: Bool {
        guard !customUrl.isEmpty else { return true }
        guard let url = URL(string: customUrl),
              let scheme = url.scheme,
              scheme == "http" || scheme == "https",
              url.host != nil else { return false }
        return true
    }

    var canPlay: Bool {
        !customUrl.isEmpty && isUrlValid
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    TextField("Paste Your Manifest URL", text: $customUrl)
                        .padding()
                        .background(AppTheme.surface)
                        .cornerRadius(8)
                        .foregroundColor(AppTheme.onSurface)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(!isUrlValid && !customUrl.isEmpty ? AppTheme.error : Color.clear, lineWidth: 1)
                        )

                    if !isUrlValid, !customUrl.isEmpty {
                        Text("Enter a valid URL starting with http:// or https://")
                            .font(.caption)
                            .foregroundColor(AppTheme.error)
                    }

                    NavigationLink(value: AppRoute.player(
                        videoUrl: customUrl.trimmingCharacters(in: .whitespacesAndNewlines),
                        customEngineUrl: nil
                    )) {
                        Text("Play URL")
                            .fontWeight(.bold)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(canPlay ? AppTheme.primary : AppTheme.onSurface.opacity(0.12))
                            .foregroundColor(canPlay ? AppTheme.onPrimary : AppTheme.onSurface.opacity(0.38))
                            .cornerRadius(8)
                    }
                    .disabled(!canPlay)
                }
                .padding(.top, 16)

                Divider().background(AppTheme.onSurfaceVariant)

                Text("Samples")
                    .font(.title3)
                    .fontWeight(.medium)
                    .foregroundColor(AppTheme.onSurfaceVariant)

                LazyVStack(spacing: 8) {
                    ForEach(VideoStreams.samples, id: \.self) { stream in
                        NavigationLink(value: AppRoute.player(
                            videoUrl: stream.uri,
                            customEngineUrl: stream.customEngineUrl
                        )) {
                            HStack(spacing: 16) {
                                Image(systemName: "play.circle")
                                    .foregroundColor(AppTheme.primary)
                                    .font(.title2)

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(stream.title)
                                        .fontWeight(.bold)
                                        .foregroundColor(AppTheme.onBackground)
                                        .lineLimit(nil)
                                        .fixedSize(horizontal: false, vertical: true)
                                        .multilineTextAlignment(.leading)
                                    Text(stream.description)
                                        .font(.caption)
                                        .foregroundColor(AppTheme.onSurfaceVariant)
                                        .lineLimit(1)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .foregroundColor(AppTheme.onSurfaceVariant)
                            }
                            .padding()
                            .background(AppTheme.surface)
                            .cornerRadius(8)
                        }
                    }
                }
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 16)
        }
        .background(AppTheme.background.edgesIgnoringSafeArea(.all))
        .navigationTitle("P2P Media Loader")
        .navigationBarTitleDisplayMode(.inline)
    }
}
