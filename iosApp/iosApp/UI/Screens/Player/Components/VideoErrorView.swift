import SwiftUI

struct VideoErrorView: View {
    let errorMessage: String
    let onBackClick: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 64))
                .foregroundColor(AppTheme.error)

            Text("Unable to Play Video")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(AppTheme.onBackground)

            Text(errorMessage)
                .font(.body)
                .multilineTextAlignment(.center)
                .foregroundColor(AppTheme.onSurfaceVariant)

            Button(action: onBackClick) {
                Text("Go Back")
                    .fontWeight(.bold)
                    .padding()
                    .background(AppTheme.error)
                    .foregroundColor(AppTheme.onError)
                    .cornerRadius(8)
            }
            .padding(.top, 16)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AppTheme.background)
    }
}
