package com.novage.p2pml.demo.ui.screens.list

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novage.p2pml.demo.data.MediaSample
import com.novage.p2pml.demo.data.VideoStreams

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(onVideoSelected: (String, String?) -> Unit) {
    var customUrl by remember { mutableStateOf("") }
    val isUrlValid by remember(customUrl) {
        derivedStateOf {
            if (customUrl.isBlank()) return@derivedStateOf true

            val hasProtocol = customUrl.startsWith("http://") || customUrl.startsWith("https://")
            val isWebUrl = Patterns.WEB_URL.matcher(customUrl).matches()

            hasProtocol && isWebUrl
        }
    }

    val canPlay = remember(customUrl, isUrlValid) {
        customUrl.isNotBlank() && isUrlValid
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("P2P Media Loader") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                label = { Text("Paste Your Manifest URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = !isUrlValid && customUrl.isNotEmpty(),
                trailingIcon = if (!isUrlValid) {
                    {
                        Icon(
                            Icons.Default.Warning,
                            "Invalid URL", tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    null
                },
                supportingText = {
                    if (!isUrlValid) {
                        Text(
                            text = "Enter a valid URL starting with http:// or https://",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            Button(
                onClick = { onVideoSelected(customUrl.trim(), null) },
                enabled = canPlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text("Play URL")
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Text(
                text = "Samples",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(VideoStreams.samples) { stream ->
                    StreamListItem(stream) {
                        onVideoSelected(stream.uri, stream.customEngineUrl)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamListItem(stream: MediaSample, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small),
        headlineContent = {
            Text(stream.title, fontWeight = FontWeight.Bold)
        },
        supportingContent = {
            Text(
                stream.description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.PlayCircleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}
