package com.novage.p2pml.demo.ui.screens.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novage.p2pml.demo.ui.screens.player.models.AvailableTracks
import com.novage.p2pml.demo.ui.screens.player.models.MediaTrack

@Composable
fun QualityDialog(availableTracks: AvailableTracks, onDismiss: () -> Unit, onTrackSelected: (MediaTrack) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Playback Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (availableTracks.videoTracks.isNotEmpty()) {
                    Text(
                        text = "Video Quality",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                    )

                    availableTracks.videoTracks.forEach { track ->
                        TrackRow(track = track, onTrackSelected = onTrackSelected)
                    }
                }

                if (availableTracks.audioTracks.size > 1) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = "Audio Track",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )

                    availableTracks.audioTracks.forEach { track ->
                        TrackRow(track = track, onTrackSelected = onTrackSelected)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun TrackRow(track: MediaTrack, onTrackSelected: (MediaTrack) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTrackSelected(track) }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = track.isSelected,
            onClick = { onTrackSelected(track) },
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = track.label,
            color = if (track.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
