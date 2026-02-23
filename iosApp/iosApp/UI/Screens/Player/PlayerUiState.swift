import Foundation

struct PlayerUiState {
    var isVideoReady: Bool = false
    var isP2PActive: Bool = false
    var fatalError: String? = nil
    var userMessage: String? = nil

    var totalDownloaded: Int64 = 0
    var p2pDownloaded: Int64 = 0
    var httpDownloaded: Int64 = 0
    var uploadTotal: Int64 = 0
    var peerCount: Int = 0

    var availableTracks = AvailableTracks()
}
