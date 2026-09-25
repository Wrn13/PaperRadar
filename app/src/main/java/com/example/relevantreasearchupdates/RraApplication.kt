package com.example.relevantreasearchupdates

import android.app.Application
import com.example.relevantreasearchupdates.data.AppDatabase
import com.example.relevantreasearchupdates.data.PaperRetention
import com.example.relevantreasearchupdates.data.SettingsRepository
import com.example.relevantreasearchupdates.notifications.NotificationHelper
import com.example.relevantreasearchupdates.work.StaleMatchCleaner
import com.example.relevantreasearchupdates.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)

        CoroutineScope(Dispatchers.IO).launch {
            val hours = SettingsRepository(this@RraApplication).settings.first().pollIntervalHours
            WorkScheduler.ensurePeriodicScheduled(this@RraApplication, hours)

            val db = AppDatabase.getInstance(this@RraApplication)

            // One-time cleanup: papers stored before author matching was tightened stay in the
            // feed forever otherwise, since they're only ever inserted, never re-checked.
            StaleMatchCleaner.purge(db)

            // Cap stored papers and clean up any cached PDFs left behind by deleted/trimmed
            // papers, so storage use doesn't grow without bound.
            PaperRetention.enforce(this@RraApplication, db)
        }
    }
}
