package com.novage.p2pml.demo.ui.screens.player.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import com.novage.p2pml.demo.ui.screens.player.PlayerUiState
import com.novage.p2pml.demo.ui.screens.player.models.VideoQuality

@Composable
fun PlayerContent(
    uiState: PlayerUiState,
    player: Player?,
    onBackClick: () -> Unit,
    onQualitySelected: (VideoQuality) -> Unit
) {
    var showQualityDialog by remember { mutableStateOf(false) }
    val isInitialLoading = !uiState.isVideoReady

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        VideoPlayerSurface(
            player = player,
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
            onQualitySelected = { quality ->
                onQualitySelected(quality)
                showQualityDialog = false
            }
        )
    }
}
