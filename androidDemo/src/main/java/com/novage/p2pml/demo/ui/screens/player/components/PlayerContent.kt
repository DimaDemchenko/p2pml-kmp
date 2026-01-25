package com.novage.p2pml.demo.ui.screens.player.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.novage.p2pml.demo.ui.screens.player.PlayerUiState
import com.novage.p2pml.demo.ui.screens.player.PlayerViewModel
import com.novage.p2pml.demo.ui.theme.TextWhite

@Composable
fun PlayerContent(uiState: PlayerUiState, viewModel: PlayerViewModel, onBackClick: () -> Unit) {
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
