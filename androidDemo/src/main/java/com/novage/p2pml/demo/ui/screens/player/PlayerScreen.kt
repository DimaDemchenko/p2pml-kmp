package com.novage.p2pml.demo.ui.screens.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.novage.p2pml.demo.ui.components.StatCard
import com.novage.p2pml.demo.ui.screens.player.components.QualityDialog
import com.novage.p2pml.demo.ui.theme.BackgroundDark
import com.novage.p2pml.demo.ui.theme.HttpBlue
import com.novage.p2pml.demo.ui.theme.P2PGreen
import com.novage.p2pml.demo.ui.theme.TextWhite
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

private const val ASPECT_RATIO = 16f / 9f
private const val BYTES_PER_KB = 1024.0

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(videoUrl: String, onBackClick: () -> Unit, viewModel: PlayerViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showQualityDialog by remember { mutableStateOf(false) }

    PlayerLifecycleObserver(viewModel)

    LaunchedEffect(videoUrl) {
        viewModel.initializePlayer(context, videoUrl)
    }

    val isInitialLoading = !uiState.isVideoReady

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextWhite
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ASPECT_RATIO)
                .background(Color.Black)
        ) {
            if (viewModel.player != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = viewModel.player
                            useController = true
                            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            IconButton(
                onClick = { showQualityDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Quality Settings",
                    tint = Color.White
                )
            }

            if (uiState.isP2PActive) {
                Badge(
                    containerColor = P2PGreen.copy(alpha = 0.8f),
                    contentColor = TextWhite,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text("P2P ON", modifier = Modifier.padding(4.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Live Statistics",
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    label = "HTTP",
                    value = formatBytes(uiState.httpDownloaded),
                    color = HttpBlue,
                    modifier = Modifier.weight(1f),
                    isLoading = isInitialLoading
                )
                StatCard(
                    label = "P2P",
                    value = formatBytes(uiState.p2pDownloaded),
                    color = P2PGreen,
                    modifier = Modifier.weight(1f),
                    isLoading = isInitialLoading
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    label = "Upload",
                    value = formatBytes(uiState.uploadTotal),
                    color = Color.Gray,
                    modifier = Modifier.weight(1f),
                    isLoading = isInitialLoading
                )
                StatCard(
                    label = "Peers",
                    value = "${uiState.peers.size}",
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    isLoading = isInitialLoading
                )
            }

            uiState.errorMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Error: $it",
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showQualityDialog) {
        QualityDialog(
            qualities = uiState.qualities,
            onDismiss = { showQualityDialog = false },
            onQualitySelected = { quality ->
                viewModel.changeQuality(quality)
                showQualityDialog = false
            }
        )
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < BYTES_PER_KB) return "$bytes B"

    val exp = (ln(bytes.toDouble()) / ln(BYTES_PER_KB)).toInt()
    val pre = "KMGTPE"[exp - 1]

    return String.format(Locale.US, "%.1f %sB", bytes / BYTES_PER_KB.pow(exp.toDouble()), pre)
}
