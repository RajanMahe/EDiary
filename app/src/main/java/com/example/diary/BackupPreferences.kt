package com.example.diary

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("backup_prefs")

object BackupPreferences {

    /* ---------------- KEYS ---------------- */

    private val LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")
    private val LOCK_MODE_KEY = stringPreferencesKey("lock_mode")
    private val LAST_OPENED_DATE = stringPreferencesKey("last_opened_date")
    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")


    /* ---------------- BACKUP ---------------- */

    suspend fun setLastBackupTime(context: Context, timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_BACKUP_TIME] = timestamp
        }
    }

    fun lastBackupTime(context: Context): Flow<Long?> =
        context.dataStore.data.map { prefs ->
            prefs[LAST_BACKUP_TIME]
        }

    /* ---------------- APP LOCK ---------------- */

    suspend fun setLockMode(context: Context, mode: LockMode) {
        context.dataStore.edit { prefs ->
            prefs[LOCK_MODE_KEY] = mode.name
        }
    }

    fun lockMode(context: Context): Flow<LockMode> =
        context.dataStore.data.map { prefs ->

            // ✅ NEW FORMAT (String)
            prefs[stringPreferencesKey("lock_mode")]
                ?.let { runCatching { LockMode.valueOf(it) }.getOrNull() }

            // ✅ OLD FORMAT (Long) — backward compatibility
                ?: prefs[longPreferencesKey("lock_mode")]
                    ?.let { ordinal ->
                        LockMode.values().getOrNull(ordinal.toInt())
                    }

                // ✅ SAFE DEFAULT
                ?: LockMode.OFF
        }


    /* ---------------- LAST OPENED DATE ---------------- */

    fun lastOpenedDate(context: Context): Flow<String?> =
        context.dataStore.data.map { prefs ->
            prefs[LAST_OPENED_DATE]
        }

    suspend fun setLastOpenedDate(context: Context, date: String) {
        context.dataStore.edit { prefs ->
            prefs[LAST_OPENED_DATE] = date
        }
    }

    suspend fun setThemeMode(context: Context, mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name
        }
    }

    fun themeMode(context: Context): Flow<ThemeMode> =
        context.dataStore.data.map { prefs ->
            prefs[THEME_MODE_KEY]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.LIGHT
        }

}
