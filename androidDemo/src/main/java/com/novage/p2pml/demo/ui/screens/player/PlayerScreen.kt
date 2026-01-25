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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextAlign
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
fun PlayerScreen(
    videoUrl: String,
    onBackClick: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    PlayerLifecycleObserver(viewModel)

    LaunchedEffect(videoUrl) { viewModel.initializePlayer(context, videoUrl) }

    uiState.userMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageConsumed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = BackgroundDark,
        modifier = Modifier.systemBarsPadding()
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (uiState.fatalError != null) {
                FatalErrorView(
                    errorMessage = uiState.fatalError ?: "Unknown Error",
                    onBackClick = onBackClick
                )
            } else {
                PlayerContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onBackClick = onBackClick
                )
            }
        }
    }
}

@Composable
private fun FatalErrorView(errorMessage: String, onBackClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Unable to Play Video",
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Go Back")
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    var showQualityDialog by remember { mutableStateOf(false) }
    val isInitialLoading = !uiState.isVideoReady

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextWhite)
        }

        VideoPlayerSurface(
            viewModel = viewModel,
            isP2PActive = uiState.isP2PActive,
            isVideoReady = uiState.isVideoReady,
            onSettingsClick = { showQualityDialog = true }
        )

        StatsSection(
            uiState = uiState,
            isInitialLoading = isInitialLoading
        )
    }

    if (showQualityDialog) {
        QualityDialog(
            qualities = uiState.qualities,
            onDismiss = { showQualityDialog = false },
            onQualitySelected = {
                viewModel.changeQuality(it)
                showQualityDialog = false
            }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerSurface(
    viewModel: PlayerViewModel,
    isP2PActive: Boolean,
    isVideoReady: Boolean,
    onSettingsClick: () -> Unit
) {
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
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(Icons.Filled.Settings, "Quality Settings", tint = Color.White)
        }

        if (isVideoReady) {
            val badgeColor = if (isP2PActive) P2PGreen else MaterialTheme.colorScheme.error
            val badgeText = if (isP2PActive) "P2P ON" else "P2P OFF"

            Badge(
                containerColor = badgeColor.copy(alpha = 0.8f),
                contentColor = TextWhite,
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
private fun StatsSection(uiState: PlayerUiState, isInitialLoading: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        if (!uiState.isP2PActive && !isInitialLoading) {
            P2PDisabledBanner()
        } else {
            LiveStatsGrid(uiState, isInitialLoading)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun P2PDisabledBanner() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, "Error", tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "P2P Disabled",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Engine failed. Playing via standard HTTP.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun LiveStatsGrid(uiState: PlayerUiState, isInitialLoading: Boolean) {
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
}

fun formatBytes(bytes: Long): String {
    if (bytes < BYTES_PER_KB) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(BYTES_PER_KB)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %sB", bytes / BYTES_PER_KB.pow(exp.toDouble()), pre)
}
