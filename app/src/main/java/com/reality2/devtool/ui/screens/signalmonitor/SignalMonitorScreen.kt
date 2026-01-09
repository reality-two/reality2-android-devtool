package com.reality2.devtool.ui.screens.signalmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reality2.devtool.ui.components.SignalItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalMonitorScreen(
    viewModel: SignalMonitorViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val signals by viewModel.signals.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Signal Monitor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearSignals() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (signals.isEmpty()) {
                Text(
                    text = "No signals received yet",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(signals, key = { "${it.timestamp}-${it.data.signal}" }) { signal ->
                        SignalItem(signal = signal)
                    }
                }
            }
        }
    }
}
