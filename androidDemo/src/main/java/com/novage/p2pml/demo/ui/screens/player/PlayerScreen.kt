package com.novage.p2pml.demo.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novage.p2pml.demo.ui.screens.player.components.PlayerContent
import com.novage.p2pml.demo.ui.screens.player.components.VideoErrorView
import com.novage.p2pml.demo.ui.theme.BackgroundDark

@Composable
fun PlayerScreen(videoUrl: String, onBackClick: () -> Unit, viewModel: PlayerViewModel = viewModel()) {
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
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (uiState.fatalError != null) {
                VideoErrorView(
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
