package com.novage.p2pml.demo.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

private const val ASPECT_RATIO = 16f / 9f

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerSurface(player: Player?, isP2PActive: Boolean, isVideoReady: Boolean, onSettingsClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ASPECT_RATIO)
            .background(Color.Black)
    ) {
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    }
                },
                update = { playerView ->
                    playerView.player = player
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!isVideoReady) {
            LoadingOverlay()
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(Icons.Filled.Settings, "Quality Settings", tint = Color.White)
        }

        if (isVideoReady) {
            val badgeColor = if (isP2PActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
            val badgeText = if (isP2PActive) "P2P ON" else "P2P OFF"

            Badge(
                containerColor = badgeColor.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text(badgeText, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Loading Stream...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
