package com.novage.p2pml.demo.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novage.p2pml.demo.ui.screens.player.components.PlayerContent
import com.novage.p2pml.demo.ui.screens.player.components.VideoErrorView

@Composable
fun PlayerScreen(onBackClick: () -> Unit, viewModel: PlayerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    PlayerLifecycleObserver(viewModel)

    uiState.userMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageConsumed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.systemBarsPadding()
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (uiState.fatalError != null) {
                VideoErrorView(
                    errorMessage = uiState.fatalError ?: "Unknown Error",
                    onBackClick = onBackClick
                )
            } else {
                PlayerContent(
                    uiState = uiState,
                    player = viewModel.player,
                    onBackClick = onBackClick,
                    onQualitySelected = { quality -> viewModel.changeQuality(quality) }
                )
            }
        }
    }
}
