package com.example.relevantreasearchupdates.ui.paperdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.relevantreasearchupdates.data.AppDatabase
import com.example.relevantreasearchupdates.data.Paper
import com.example.relevantreasearchupdates.data.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PaperDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val settingsRepository = SettingsRepository(application)

    private val paperId = MutableStateFlow<Long?>(null)

    val paper: StateFlow<Paper?> = paperId
        .flatMapLatest { id -> if (id == null) flowOf(null) else db.paperDao().observeById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val ezproxyUrlTemplate: StateFlow<String> = settingsRepository.settings
        .map { it.ezproxyUrlTemplate }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun load(id: Long) {
        if (paperId.value == id) return
        paperId.value = id
        viewModelScope.launch { db.paperDao().markRead(id) }
    }
}
