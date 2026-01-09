package com.reality2.devtool.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.reality2.devtool.data.model.SignalNotification
import com.reality2.devtool.util.toTimeString
import kotlinx.serialization.json.Json

@Composable
fun SignalItem(
    signal: SignalNotification,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = signal.data.signal,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Sentant: ${signal.data.sentant_id.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = parseTimestamp(signal.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }

            // Event info
            Text(
                text = "Event: ${signal.data.event}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Expanded details
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                // Parameters
                if (signal.data.parameters.isNotEmpty()) {
                    Text(
                        text = "Parameters:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = Json.encodeToString(
                            kotlinx.serialization.json.JsonObject.serializer(),
                            signal.data.parameters
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Passthrough data if available
                if (signal.data.passthrough != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Passthrough:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = signal.data.passthrough.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun parseTimestamp(timestamp: String): String {
    // Simple timestamp parsing - just show the time portion
    return try {
        // ISO8601 format: "2025-01-08T12:00:00Z"
        val time = timestamp.substringAfter('T').substringBefore('Z')
        time
    } catch (e: Exception) {
        timestamp
    }
}
