package com.reality2.devtool.ui.screens.signalmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality2.devtool.data.model.SignalNotification
import com.reality2.devtool.data.repository.Reality2Repository
import com.reality2.devtool.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SignalMonitorViewModel(
    private val repository: Reality2Repository
) : ViewModel() {

    private val _signals = MutableStateFlow<List<SignalNotification>>(emptyList())
    val signals: StateFlow<List<SignalNotification>> = _signals.asStateFlow()

    init {
        collectSignals()
    }

    private fun collectSignals() {
        viewModelScope.launch {
            repository.allSignals.collect { signal ->
                _signals.value = (listOf(signal) + _signals.value)
                    .take(Constants.MAX_SIGNAL_HISTORY)
            }
        }
    }

    fun clearSignals() {
        _signals.value = emptyList()
    }
}
