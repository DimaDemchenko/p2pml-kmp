import SwiftUI

struct SkeletonModifier: ViewModifier {
    let isLoading: Bool
    @State private var isAnimating = false

    func body(content: Content) -> some View {
        if isLoading {
            content
                .opacity(isAnimating ? 0.3 : 0.6)
                .onAppear {
                    withAnimation(.linear(duration: 1.0).repeatForever(autoreverses: true)) {
                        isAnimating = true
                    }
                }
        } else {
            content
        }
    }
}

extension View {
    func skeleton(isLoading: Bool) -> some View {
        self.modifier(SkeletonModifier(isLoading: isLoading))
    }
}
