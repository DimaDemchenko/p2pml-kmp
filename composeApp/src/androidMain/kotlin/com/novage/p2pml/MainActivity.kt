package com.novage.p2pml

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var p2pMediaLoader: P2PMediaLoader

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        p2pMediaLoader = P2PMediaLoader()
        p2pMediaLoader.start()

        val manifestUrl =
            p2pMediaLoader.getManifestUrl(
                "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8"
            )

        val mediaSource =
            HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
                .createMediaSource(MediaItem.fromUri(manifestUrl))
        player =
            ExoPlayer.Builder(this).build().apply {
                setMediaSource(mediaSource)
                playWhenReady = true
            }

        super.onCreate(savedInstanceState)
        setContent { ExoPlayerScreen(player) }
    }
}

@Composable
fun ExoPlayerScreen(player: ExoPlayer) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            PlayerView(context).apply {
                // Set the player instance
                this.player = player
                // Optionally enable playback controls
                useController = true
            }
        },
    )
}
