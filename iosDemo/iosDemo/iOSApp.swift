import SwiftUI
import P2PML

@main
struct iOSApp: App {

    init() {
        P2PMediaLoader.companion.enableLogging()
        P2PFileLogSink.install()

        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(AppTheme.background)
        appearance.titleTextAttributes = [.foregroundColor: UIColor(AppTheme.onBackground)]
        appearance.largeTitleTextAttributes = [.foregroundColor: UIColor(AppTheme.onBackground)]

        UINavigationBar.appearance().standardAppearance = appearance
        UINavigationBar.appearance().compactAppearance = appearance
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
        UINavigationBar.appearance().tintColor = UIColor(AppTheme.onBackground)
    }

    var body: some Scene {
        WindowGroup {
            NavigationStack {
                VideoListScreen()
                    .navigationDestination(for: AppRoute.self) { route in
                        switch route {
                        case .player(let videoUrl, let customEngineUrl):
                            PlayerScreen(videoUrl: videoUrl, customEngineUrl: customEngineUrl)
                        }
                    }
            }
            .preferredColorScheme(.dark)
        }
    }
}
