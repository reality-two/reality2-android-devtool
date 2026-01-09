package com.reality2.devtool.ui.screens.sentantlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality2.devtool.data.model.Reality2Node
import com.reality2.devtool.data.model.Sentant
import com.reality2.devtool.data.repository.Reality2Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

class SentantListViewModel(
    private val repository: Reality2Repository,
    private val nodeAddress: String
) : ViewModel() {

    private val _node = MutableStateFlow<Reality2Node?>(null)
    val node: StateFlow<Reality2Node?> = _node.asStateFlow()

    private val _sentants = MutableStateFlow<List<Sentant>>(emptyList())
    val sentants: StateFlow<List<Sentant>> = _sentants.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadNode()
        querySentants()
    }

    private fun loadNode() {
        _node.value = repository.getConnectedNode(nodeAddress)
    }

    fun querySentants() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.querySentants(nodeAddress)
                .onSuccess { sentantsList ->
                    Timber.d("Loaded ${sentantsList.size} sentants")
                    _sentants.value = sentantsList
                    loadNode() // Refresh node data
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to query sentants")
                    _error.value = error.message ?: "Failed to query sentants"
                }

            _isLoading.value = false
        }
    }

    fun sendEvent(sentantId: String, event: String, parameters: JsonObject) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.sendEvent(nodeAddress, sentantId, event, parameters)
                .onSuccess {
                    Timber.d("Event sent successfully")
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to send event")
                    _error.value = error.message ?: "Failed to send event"
                }

            _isLoading.value = false
        }
    }
}
