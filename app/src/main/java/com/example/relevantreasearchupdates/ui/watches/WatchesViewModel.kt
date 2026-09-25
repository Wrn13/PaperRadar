package com.example.relevantreasearchupdates.ui.watches

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.relevantreasearchupdates.data.AppDatabase
import com.example.relevantreasearchupdates.data.Watch
import com.example.relevantreasearchupdates.data.WatchType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WatchesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    val watches: StateFlow<List<Watch>> = db.watchDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addWatch(type: WatchType, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            db.watchDao().insert(Watch(type = type, value = trimmed))
        }
    }

    fun deleteWatch(watch: Watch) {
        viewModelScope.launch {
            db.watchDao().delete(watch)
        }
    }
}
