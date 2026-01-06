package com.novage.p2pml.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.novage.p2pml.demo.stats.P2PStats

private const val VIDEO_ASPECT_RATIO = 16f / 9f

@Suppress("FunctionNaming")
@Composable
fun ExoPlayerScreen(
    player: ExoPlayer?,
    videoTitle: String,
    isLoading: Boolean,
    stats: P2PStats,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f))) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(VIDEO_ASPECT_RATIO),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply { this.player = player }
                },
                update = { playerView -> playerView.player = player },
            )

            if (isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Text(
            text = videoTitle,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(16.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            P2PStatistics(
                totalHttpDownloaded = stats.bytesDownloadedHttp / (1024 * 1024).toDouble(),
                totalP2PDownloaded = stats.bytesDownloadedP2p / (1024 * 1024).toDouble(),
                totalP2PUploaded = stats.bytesUploaded / (1024 * 1024).toDouble(),
                activePeers = stats.connectedPeers.size,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
