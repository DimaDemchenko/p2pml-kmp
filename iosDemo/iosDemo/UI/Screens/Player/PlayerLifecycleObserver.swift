import SwiftUI

struct PlayerLifecycleObserver: ViewModifier {
    let viewModel: PlayerViewModel

    func body(content: Content) -> some View {
        content
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)) { _ in
                viewModel.onAppBackgrounded()
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
                viewModel.onAppForegrounded()
            }
    }
}
