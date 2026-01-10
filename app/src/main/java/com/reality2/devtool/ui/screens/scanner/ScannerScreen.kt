package com.reality2.devtool.ui.screens.scanner

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.reality2.devtool.data.model.ConnectionState
import com.reality2.devtool.ui.components.NodeCard
import com.reality2.devtool.ui.dialogs.HotspotConnectionDialog
import com.reality2.devtool.ui.dialogs.NodeInfoDialog
import com.reality2.devtool.ui.dialogs.SentantsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val nodes by viewModel.discoveredNodes.collectAsState()
    val sentantsDialogData by viewModel.showSentantsDialog.collectAsState()
    val nodeInfoDialogData by viewModel.showNodeInfoDialog.collectAsState()
    val hotspotDialogData by viewModel.showHotspotDialog.collectAsState()

    // Animated dots for scanning indicator
    var dotCount by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dotCount = if (dotCount >= 3) 1 else dotCount + 1
        }
    }
    val dots = ".".repeat(dotCount)

    // Auto-start scanning when screen is shown
    LaunchedEffect(Unit) {
        viewModel.startScan()
    }

    // Stop scanning when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reality2 Nodes") },
                actions = {
                    IconButton(onClick = { viewModel.restartScan() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restart scanning")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Status indicator - always scanning
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sensor icon
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "Scanning",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Scanning for Reality2 nodes$dots",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Nodes list
            if (nodes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No nodes found yet...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(nodes, key = { it.address }) { node ->
                        NodeCard(
                            node = node,
                            onConnect = { viewModel.connectToNode(node) },
                            onShowNodeInfo = { viewModel.showNodeInfo(node) },
                            onClick = {
                                // Tapping a connected node shows its info
                                if (node.connectionState == ConnectionState.CONNECTED) {
                                    viewModel.showNodeInfo(node)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Show Sentants Dialog when sentants are queried
    sentantsDialogData?.let { data ->
        SentantsDialog(
            sentants = data.sentants,
            rawJson = data.rawJson,
            onDismiss = { viewModel.dismissSentantsDialog() }
        )
    }

    // Show Node Info Dialog after connecting
    nodeInfoDialogData?.let { data ->
        NodeInfoDialog(
            nodeName = data.nodeName,
            nodeAddress = data.nodeAddress,
            nodeId = data.nodeId,
            rssi = data.rssi,
            sentantsCount = data.sentantsCount,
            json = data.json,
            onDismiss = { viewModel.dismissNodeInfoDialog() }
        )
    }

    // Show Hotspot Connection Dialog when hotspot info is available
    hotspotDialogData?.let { data ->
        HotspotConnectionDialog(
            nodeName = data.nodeName,
            hotspotInfo = data.hotspotInfo,
            onQuerySentants = { viewModel.confirmQuerySentants() },
            onDismiss = { viewModel.dismissHotspotDialog() }
        )
    }
}
