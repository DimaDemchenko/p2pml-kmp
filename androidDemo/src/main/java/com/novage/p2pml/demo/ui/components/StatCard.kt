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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novage.p2pml.demo.ui.theme.P2PDemoTheme

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

@Preview
@Composable
private fun StatCardPreview() {
    P2PDemoTheme {
        StatCard(label = "P2P", value = "1.2 MB", color = Color(0xFF4CAF50))
    }
}

@Preview
@Composable
private fun StatCardLoadingPreview() {
    P2PDemoTheme {
        StatCard(label = "P2P", value = "", color = Color(0xFF4CAF50), isLoading = true)
    }
}
