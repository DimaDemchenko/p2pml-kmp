import SwiftUI

struct StatCard: View {
    let label: String
    let value: String
    let color: Color
    var isLoading: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundColor(AppTheme.onSurfaceVariant)

            if isLoading {
                RoundedRectangle(cornerRadius: 4)
                    .fill(AppTheme.onSurfaceVariant.opacity(0.3))
                    .frame(width: 70, height: 24)
                    .skeleton(isLoading: true)
            } else {
                Text(value)
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(color)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.surface)
        .cornerRadius(12)
    }
}
