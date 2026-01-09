package com.reality2.devtool.ui.screens.sentantlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.reality2.devtool.data.repository.Reality2Repository

class SentantListViewModelFactory(
    private val repository: Reality2Repository,
    private val nodeAddress: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SentantListViewModel::class.java)) {
            return SentantListViewModel(repository, nodeAddress) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
