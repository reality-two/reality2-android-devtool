package com.reality2.devtool.ui.screens.signalmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.reality2.devtool.data.repository.Reality2Repository

class SignalMonitorViewModelFactory(
    private val repository: Reality2Repository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SignalMonitorViewModel::class.java)) {
            return SignalMonitorViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
