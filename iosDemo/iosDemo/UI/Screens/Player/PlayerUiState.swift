import Foundation

struct PlayerUiState {
    var isVideoReady: Bool = false
    var isP2PActive: Bool = false
    var fatalError: String?
    var userMessage: String?

    var totalDownloaded: Int64 = 0
    var p2pDownloaded: Int64 = 0
    var httpDownloaded: Int64 = 0
    var uploadTotal: Int64 = 0
    var peers: [String] = []

    var availableTracks = AvailableTracks()
}
