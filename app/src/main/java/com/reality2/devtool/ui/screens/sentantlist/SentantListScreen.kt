package com.reality2.devtool.ui.screens.sentantlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reality2.devtool.data.model.Sentant
import com.reality2.devtool.ui.components.SentantCard
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentantListScreen(
    viewModel: SentantListViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val node by viewModel.node.collectAsState()
    val sentants by viewModel.sentants.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var selectedSentant by remember { mutableStateOf<Sentant?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sentants") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.querySentants() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
            when {
                isLoading && sentants.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                error != null && sentants.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error: $error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.querySentants() }) {
                            Text("Retry")
                        }
                    }
                }

                sentants.isEmpty() -> {
                    Text(
                        text = "No sentants available",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sentants, key = { it.id }) { sentant ->
                            SentantCard(
                                sentant = sentant,
                                onSendEvent = { selectedSentant = sentant }
                            )
                        }
                    }
                }
            }
        }
    }

    // Event dialog
    if (selectedSentant != null) {
        EventDialog(
            sentant = selectedSentant!!,
            onDismiss = { selectedSentant = null },
            onSend = { event, parameters ->
                viewModel.sendEvent(selectedSentant!!.id, event, parameters)
                selectedSentant = null
            }
        )
    }
}

@Composable
private fun EventDialog(
    sentant: Sentant,
    onDismiss: () -> Unit,
    onSend: (String, JsonObject) -> Unit
) {
    var selectedEvent by remember { mutableStateOf(sentant.events.firstOrNull()?.event ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Event to ${sentant.name}") },
        text = {
            Column {
                Text("Select an event to send:")
                Spacer(modifier = Modifier.height(8.dp))
                sentant.events.forEach { event ->
                    TextButton(
                        onClick = { selectedEvent = event.event },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = event.event,
                            color = if (selectedEvent == event.event)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(selectedEvent, JsonObject(emptyMap())) },
                enabled = selectedEvent.isNotEmpty()
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
