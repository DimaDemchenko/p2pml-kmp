package com.novage.p2pml.demo.ui.screens.player.utils

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.novage.p2pml.demo.ui.screens.player.models.AvailableTracks
import com.novage.p2pml.demo.ui.screens.player.models.MediaTrack

private const val BITRATE_DIVISOR = 1000
private const val LABEL_SEPARATOR = " • "

@OptIn(UnstableApi::class)
fun getAvailableTracks(tracks: Tracks, params: TrackSelectionParameters): AvailableTracks {
    val isVideoAuto = params.overrides.values.none { it.mediaTrackGroup.type == C.TRACK_TYPE_VIDEO }
    val isAudioAuto = params.overrides.values.none { it.mediaTrackGroup.type == C.TRACK_TYPE_AUDIO }

    val videoTracks = mutableListOf(MediaTrack("Auto", isVideoAuto, -1, -1, C.TRACK_TYPE_VIDEO, true))
    val audioTracks = mutableListOf<MediaTrack>()

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

    // A lone rendition is not a choice: "Default" and the track itself resolve to the same audio,
    // so the section is only worth offering when the stream has alternatives.
    val audioWithDefault = if (distinctAudio.size > 1) {
        listOf(MediaTrack("Default", isAudioAuto, -1, -1, C.TRACK_TYPE_AUDIO, true)) + distinctAudio
    } else {
        emptyList()
    }

    return AvailableTracks(videoTracks = sortedVideo, audioTracks = audioWithDefault)
}

private fun extractTracksFromGroup(
    group: Tracks.Group,
    groupIndex: Int,
    trackType: Int,
    isAutoSelected: Boolean,
    labelFormatter: (Format) -> String?
): List<MediaTrack> = (0 until group.length)
    .filter { group.isTrackSupported(it) }
    .mapNotNull { trackIndex ->
        val label = labelFormatter(group.getTrackFormat(trackIndex)) ?: return@mapNotNull null

        MediaTrack(
            label = label,
            isSelected = !isAutoSelected && group.isTrackSelected(trackIndex),
            groupIndex = groupIndex,
            trackIndex = trackIndex,
            trackType = trackType
        )
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
private fun formatAudioLabel(format: Format): String? {
    val name = format.label ?: format.language ?: return null

    val codec = audioCodecName(format.sampleMimeType)
    val bitrate = if (format.bitrate > 0) "${format.bitrate / BITRATE_DIVISOR} kbps" else null

    return listOfNotNull(name, codec, bitrate).joinToString(LABEL_SEPARATOR)
}

/**
 * Gives the codecs whose MIME subtype reads badly ("mp4a-latm", "eac3-joc") their canonical short
 * name. Everything else falls back to the subtype: an unrecognised codec still has to tell two
 * identically named renditions apart, and dropping it would let [getAvailableTracks] collapse them.
 * Names stay short on purpose — a rendition named "English (DVS)" leaves little room beside it.
 */
private fun audioCodecName(sampleMimeType: String?): String? = when (sampleMimeType) {
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_MPEG -> "MP3"
    MimeTypes.AUDIO_AC3 -> "AC-3"
    MimeTypes.AUDIO_E_AC3 -> "E-AC-3"
    MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3 JOC"
    MimeTypes.AUDIO_AC4 -> "AC-4"
    else -> sampleMimeType?.substringAfter('/')?.uppercase()
}
