package com.example.relevantreasearchupdates.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.relevantreasearchupdates.data.AppSettings
import com.example.relevantreasearchupdates.data.SettingsRepository
import com.example.relevantreasearchupdates.work.WorkScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun setEzproxyUrlTemplate(template: String) {
        viewModelScope.launch { repository.setEzproxyUrlTemplate(template) }
    }

    fun setPollIntervalHours(hours: Int) {
        viewModelScope.launch {
            repository.setPollIntervalHours(hours)
            WorkScheduler.reschedulePeriodic(getApplication(), hours)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setNotificationsEnabled(enabled) }
    }

    fun checkNow() {
        WorkScheduler.runOnce(getApplication())
    }
}
