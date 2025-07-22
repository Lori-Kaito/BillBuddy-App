package com.billbuddy.app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class GroupDetailViewModelFactory(
    private val application: Application,
    private val groupId: Long
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GroupDetailViewModel(application, groupId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}