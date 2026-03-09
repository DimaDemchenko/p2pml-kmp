import Foundation

enum AppRoute: Hashable {
    case player(videoUrl: String, customEngineUrl: String?)
}
