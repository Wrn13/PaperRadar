package com.example.relevantreasearchupdates.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.relevantreasearchupdates.data.AppDatabase
import com.example.relevantreasearchupdates.data.Paper
import com.example.relevantreasearchupdates.work.LiteratureCheckWorker
import com.example.relevantreasearchupdates.work.WorkScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class CheckProgress(val current: Int, val total: Int)

class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    val papers: StateFlow<List<Paper>> = db.paperDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val checkWorkInfos = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWorkFlow(LiteratureCheckWorker.UNIQUE_ONE_TIME_NAME)

    val isChecking: StateFlow<Boolean> = checkWorkInfos
        .map { infos -> infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Non-null only once the worker has reported which watch it's on, for a determinate progress bar. */
    val checkProgress: StateFlow<CheckProgress?> = checkWorkInfos
        .map { infos ->
            val info = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
            val total = info?.progress?.getInt(LiteratureCheckWorker.KEY_TOTAL, 0) ?: 0
            val current = info?.progress?.getInt(LiteratureCheckWorker.KEY_CURRENT, 0) ?: 0
            if (info != null && total > 0) CheckProgress(current, total) else null
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun checkNow() {
        WorkScheduler.runOnce(getApplication())
    }

    /** Manual escape hatch for a false-positive match string-matching can't catch, e.g. two real
     * people who happen to share the exact full name being watched. */
    fun deletePaper(id: Long) {
        viewModelScope.launch {
            db.paperDao().getById(id)?.localPdfPath?.let { File(it).delete() }
            db.paperDao().deleteById(id)
        }
    }
}
