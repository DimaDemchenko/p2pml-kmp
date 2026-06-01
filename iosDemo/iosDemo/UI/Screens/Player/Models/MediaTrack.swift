import Foundation

struct MediaTrack: Identifiable, Equatable {
    var id: String { "\(label)-\(bitrate)-\(isAudio)" }
    let label: String
    let isSelected: Bool
    let isAuto: Bool
    var bitrate: Double = 0
    var resolution: CGSize? = nil
    var isAudio: Bool = false
}

struct AvailableTracks {
    var videoTracks: [MediaTrack] = []
    var audioTracks: [MediaTrack] = []
}
