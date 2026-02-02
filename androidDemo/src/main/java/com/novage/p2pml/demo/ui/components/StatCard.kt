package com.novage.p2pml.demo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier, isLoading: Boolean = false) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(24.dp)
                        .skeleton(isLoading = true, shape = MaterialTheme.shapes.small)
                )
            } else {
                Text(
                    text = value,
                    color = color,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
