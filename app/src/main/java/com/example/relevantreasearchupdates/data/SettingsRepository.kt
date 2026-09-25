package com.example.relevantreasearchupdates.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "rra_settings")

data class AppSettings(
    val ezproxyUrlTemplate: String = "",
    val pollIntervalHours: Int = 6,
    val notificationsEnabled: Boolean = true
)

/**
 * ezproxyUrlTemplate should contain the literal token "{url}" where the target article URL
 * gets substituted, e.g. "https://libezproxy.<your-school>.edu/login?url={url}".
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val EZPROXY_TEMPLATE = stringPreferencesKey("ezproxy_url_template")
        val POLL_INTERVAL_HOURS = intPreferencesKey("poll_interval_hours")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            ezproxyUrlTemplate = prefs[Keys.EZPROXY_TEMPLATE] ?: "",
            pollIntervalHours = prefs[Keys.POLL_INTERVAL_HOURS] ?: 6,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
        )
    }

    suspend fun setEzproxyUrlTemplate(template: String) {
        context.dataStore.edit { it[Keys.EZPROXY_TEMPLATE] = template }
    }

    suspend fun setPollIntervalHours(hours: Int) {
        context.dataStore.edit { it[Keys.POLL_INTERVAL_HOURS] = hours }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }
}
