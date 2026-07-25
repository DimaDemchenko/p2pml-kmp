package com.novage.p2pml.demo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The demo's shared top bar: a 44dp app bar over a divider, in the app background colour.
 * Pass [centered] = true for the home screen (no nav icon); the player screen supplies a
 * [navigationIcon] and [actions].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoTopBar(
    title: String,
    centered: Boolean = false,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val titleContent = @Composable {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onBackground
    )

    Column {
        if (centered) {
            CenterAlignedTopAppBar(title = titleContent, expandedHeight = 44.dp, colors = colors)
        } else {
            TopAppBar(
                title = titleContent,
                navigationIcon = navigationIcon,
                actions = actions,
                expandedHeight = 44.dp,
                colors = colors
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
