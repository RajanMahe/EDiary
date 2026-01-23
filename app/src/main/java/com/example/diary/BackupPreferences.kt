package com.example.diary

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.longPreferencesKey


private val Context.dataStore by preferencesDataStore("backup_prefs")

object BackupPreferences {

    private val LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")
    private val LOCK_MODE = longPreferencesKey("lock_mode")

    suspend fun setLastBackupTime(
        context: Context,
        timestamp: Long
    ) {
        context.dataStore.edit { prefs ->
            prefs[LAST_BACKUP_TIME] = timestamp
        }
    }

    fun lastBackupTime(context: Context): Flow<Long?> =
        context.dataStore.data.map { prefs ->
            prefs[LAST_BACKUP_TIME]
        }

    suspend fun setLockMode(context: Context, mode: LockMode) {
        context.dataStore.edit { prefs ->
            prefs[LOCK_MODE] = mode.ordinal.toLong()
        }
    }

    fun lockMode(context: Context): Flow<LockMode> =
        context.dataStore.data.map { prefs ->
            LockMode.values()[
                prefs[LOCK_MODE]?.toInt() ?: LockMode.OFF.ordinal
            ]
        }
}