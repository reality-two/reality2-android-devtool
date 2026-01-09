package com.reality2.devtool.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reality2.devtool.data.model.ConnectionState
import com.reality2.devtool.data.model.Reality2Node
import com.reality2.devtool.ui.theme.*
import com.reality2.devtool.util.toSignalStrength

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeCard(
    node: Reality2Node,
    onConnect: () -> Unit,
    onShowNodeInfo: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row with name and connection status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = node.shortId(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Connection status badge
                ConnectionStateBadge(node.connectionState)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Signal strength row
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SignalCellular4Bar,
                    contentDescription = "Signal",
                    tint = getSignalStrengthColor(node.rssi),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${node.rssi} dBm (${node.rssi.toSignalStrength()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Error message if connection failed
            if (node.connectionState == ConnectionState.ERROR) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Connection Failed",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "The node GATT service has no characteristics. Check your Reality2 node server configuration.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Connect button if not connected
            if (node.connectionState != ConnectionState.CONNECTED) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = node.connectionState != ConnectionState.CONNECTING
                ) {
                    Icon(
                        imageVector = Icons.Default.BluetoothConnected,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when (node.connectionState) {
                            ConnectionState.CONNECTING -> "Connecting..."
                            ConnectionState.ERROR -> "Retry Connection"
                            else -> "Connect"
                        }
                    )
                }
            }

            // Show Node Info button if connected
            if (node.connectionState == ConnectionState.CONNECTED) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onShowNodeInfo,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show Node Info")
                }
            }
        }
    }
}

@Composable
private fun ConnectionStateBadge(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.CONNECTED -> Connected
        ConnectionState.CONNECTING -> Connecting
        ConnectionState.DISCONNECTED -> Disconnected
        ConnectionState.ERROR -> ConnectionError
    }

    val text = when (state) {
        ConnectionState.CONNECTED -> "Connected"
        ConnectionState.CONNECTING -> "Connecting"
        ConnectionState.DISCONNECTED -> "Disconnected"
        ConnectionState.ERROR -> "Error"
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun getSignalStrengthColor(rssi: Int): androidx.compose.ui.graphics.Color {
    return when {
        rssi >= -50 -> SignalExcellent
        rssi >= -60 -> SignalGood
        rssi >= -70 -> SignalFair
        rssi >= -80 -> SignalWeak
        else -> SignalVeryWeak
    }
}
