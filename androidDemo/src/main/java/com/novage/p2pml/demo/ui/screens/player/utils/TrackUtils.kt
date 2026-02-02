package com.novage.p2pml.demo.ui.screens.player.utils

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.novage.p2pml.demo.ui.screens.player.models.VideoQuality

private const val BITRATE_DIVISOR = 1000

@OptIn(UnstableApi::class)
fun getAvailableQualities(tracks: Tracks, params: androidx.media3.common.TrackSelectionParameters): List<VideoQuality> {
    val qualities = mutableListOf<VideoQuality>()

    val isAutoSelected = params.overrides.isEmpty()

    qualities.add(
        VideoQuality(
            label = "Auto",
            isSelected = isAutoSelected,
            groupIndex = -1,
            trackIndex = -1,
            isAuto = true
        )
    )

    for (groupIndex in tracks.groups.indices) {
        val group = tracks.groups[groupIndex]
        if (group.type != C.TRACK_TYPE_VIDEO) continue

        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue

            val format = group.getTrackFormat(trackIndex)

            val isSelected = !isAutoSelected && group.isTrackSelected(trackIndex)

            qualities.add(
                VideoQuality(
                    label = formatLabel(format.height, format.bitrate),
                    isSelected = isSelected,
                    groupIndex = groupIndex,
                    trackIndex = trackIndex
                )
            )
        }
    }

    return qualities
        .distinctBy { it.label }
        .sortedWith(
            compareBy(
                { !it.isAuto },
                {
                    val height = it.label.substringBefore("p").toIntOrNull() ?: 0
                    -height
                }
            )
        )
}

fun applyTrackSelection(player: Player, quality: VideoQuality, tracks: Tracks) {
    val newParams = player.trackSelectionParameters.buildUpon()

    if (quality.isAuto) {
        newParams.clearOverrides()
    } else {
        val group = tracks.groups[quality.groupIndex].mediaTrackGroup
        newParams
            .clearOverrides()
            .addOverride(TrackSelectionOverride(group, quality.trackIndex))
    }

    player.trackSelectionParameters = newParams.build()
}

private fun formatLabel(height: Int, bitrate: Int): String {
    val resolution = if (height > 0) "${height}p" else "Unknown"
    val bitrateStr = if (bitrate > 0) " • ${bitrate / BITRATE_DIVISOR} kbps" else ""
    return "$resolution$bitrateStr"
}
