package com.novage.p2pml.demo.ui.screens.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novage.p2pml.demo.ui.components.StatCard
import com.novage.p2pml.demo.ui.screens.player.PlayerUiState
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

private const val BYTES_PER_KB = 1024.0

@Composable
fun StatsSection(uiState: PlayerUiState, isInitialLoading: Boolean) {
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Engine failed. Playing via standard HTTP.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun LiveStatsGrid(uiState: PlayerUiState, isInitialLoading: Boolean) {
    Text(
        text = "Live Statistics",
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(16.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            label = "HTTP",
            value = formatBytes(uiState.httpDownloaded),
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
            isLoading = isInitialLoading
        )

        StatCard(
            label = "P2P",
            value = formatBytes(uiState.p2pDownloaded),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            isLoading = isInitialLoading
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            label = "Upload",
            value = formatBytes(uiState.uploadTotal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            isLoading = isInitialLoading
        )
        StatCard(
            label = "Peers",
            value = "${uiState.peers.size}",
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            isLoading = isInitialLoading
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < BYTES_PER_KB) return "$bytes B"
    val prefixes = "KMGTPE"
    val exp = minOf((ln(bytes.toDouble()) / ln(BYTES_PER_KB)).toInt(), prefixes.length)
    val pre = prefixes[exp - 1]
    return String.format(Locale.US, "%.1f %sB", bytes / BYTES_PER_KB.pow(exp.toDouble()), pre)
}
