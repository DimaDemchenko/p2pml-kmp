import Foundation

struct MediaTrack: Identifiable, Equatable {
    let id = UUID()
    let label: String
    let isSelected: Bool
    let isAuto: Bool
}

struct AvailableTracks {
    var videoTracks: [MediaTrack] = []
    var audioTracks: [MediaTrack] = []
}
