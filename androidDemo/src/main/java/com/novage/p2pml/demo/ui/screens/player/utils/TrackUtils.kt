package com.novage.p2pml.demo.ui.screens.player.utils

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.novage.p2pml.demo.ui.screens.player.models.AvailableTracks
import com.novage.p2pml.demo.ui.screens.player.models.MediaTrack

private const val BITRATE_DIVISOR = 1000

@OptIn(UnstableApi::class)
fun getAvailableTracks(tracks: Tracks, params: TrackSelectionParameters): AvailableTracks {
    val isVideoAuto = params.overrides.values.none { it.mediaTrackGroup.type == C.TRACK_TYPE_VIDEO }
    val isAudioAuto = params.overrides.values.none { it.mediaTrackGroup.type == C.TRACK_TYPE_AUDIO }

    val videoTracks = mutableListOf(MediaTrack("Auto", isVideoAuto, -1, -1, C.TRACK_TYPE_VIDEO, true))
    val audioTracks = mutableListOf(MediaTrack("Default", isAudioAuto, -1, -1, C.TRACK_TYPE_AUDIO, true))

    tracks.groups.forEachIndexed { groupIndex, group ->
        when (group.type) {
            C.TRACK_TYPE_VIDEO -> {
                videoTracks.addAll(
                    extractTracksFromGroup(group, groupIndex, C.TRACK_TYPE_VIDEO, isVideoAuto, ::formatVideoLabel)
                )
            }

            C.TRACK_TYPE_AUDIO -> {
                audioTracks.addAll(
                    extractTracksFromGroup(group, groupIndex, C.TRACK_TYPE_AUDIO, isAudioAuto, ::formatAudioLabel)
                )
            }
        }
    }

    val sortedVideo = videoTracks.distinctBy { it.label }.sortedWith(
        compareBy({ !it.isAuto }, { -(it.label.substringBefore("p").toIntOrNull() ?: 0) })
    )
    val distinctAudio = audioTracks.distinctBy { it.label }

    return AvailableTracks(videoTracks = sortedVideo, audioTracks = distinctAudio)
}

private fun extractTracksFromGroup(
    group: Tracks.Group,
    groupIndex: Int,
    trackType: Int,
    isAutoSelected: Boolean,
    labelFormatter: (Format) -> String
): List<MediaTrack> {
    val extracted = mutableListOf<MediaTrack>()

    for (trackIndex in 0 until group.length) {
        if (group.isTrackSupported(trackIndex)) {
            val isSelected = !isAutoSelected && group.isTrackSelected(trackIndex)
            extracted.add(
                MediaTrack(
                    label = labelFormatter(group.getTrackFormat(trackIndex)),
                    isSelected = isSelected,
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    trackType = trackType
                )
            )
        }
    }
    return extracted
}

fun applyTrackSelection(player: Player, track: MediaTrack, tracks: Tracks) {
    val newParams = player.trackSelectionParameters.buildUpon()

    if (track.isAuto) {
        newParams.clearOverridesOfType(track.trackType)
    } else {
        val group = tracks.groups[track.groupIndex].mediaTrackGroup
        newParams
            .clearOverridesOfType(track.trackType)
            .addOverride(TrackSelectionOverride(group, track.trackIndex))
    }

    player.trackSelectionParameters = newParams.build()
}

@OptIn(UnstableApi::class)
private fun formatVideoLabel(format: Format): String {
    val resolution = if (format.height > 0) "${format.height}p" else "Unknown"
    val bitrateStr = if (format.bitrate > 0) " • ${format.bitrate / BITRATE_DIVISOR} kbps" else ""
    return "$resolution$bitrateStr"
}

@OptIn(UnstableApi::class)
private fun formatAudioLabel(format: Format): String {
    val language = format.language ?: "Unknown"
    val bitrateStr = if (format.bitrate > 0) " • ${format.bitrate / BITRATE_DIVISOR} kbps" else ""

    return language + bitrateStr
}
