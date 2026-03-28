import Foundation

struct MediaTrack: Identifiable, Equatable {
    let id = UUID()
    let label: String
    let isSelected: Bool
    let isAuto: Bool
    var bitrate: Double = 0
    var resolution: CGSize? = nil
    var isAudio: Bool = false

    static func == (lhs: MediaTrack, rhs: MediaTrack) -> Bool {
        lhs.label == rhs.label &&
            lhs.isSelected == rhs.isSelected &&
            lhs.isAuto == rhs.isAuto &&
            lhs.bitrate == rhs.bitrate &&
            lhs.resolution == rhs.resolution &&
            lhs.isAudio == rhs.isAudio
    }
}

struct AvailableTracks {
    var videoTracks: [MediaTrack] = []
    var audioTracks: [MediaTrack] = []
}
