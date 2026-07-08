import Foundation

struct MediaTrack: Identifiable, Equatable {
    var id: String {
        let bitrateKey = bitrate.isFinite ? Int64(bitrate) : 0
        return "\(label)-\(bitrateKey)-\(isAudio)-\(isAuto)"
    }

    let label: String
    let isSelected: Bool
    let isAuto: Bool
    var bitrate: Double = 0
    var resolution: CGSize?
    var isAudio: Bool = false
}

struct AvailableTracks {
    var videoTracks: [MediaTrack] = []
    var audioTracks: [MediaTrack] = []
}
