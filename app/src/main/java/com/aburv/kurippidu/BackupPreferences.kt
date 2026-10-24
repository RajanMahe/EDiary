package com.aburv.kurippidu

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
private fun lastOpenedDateKey(diaryId: Int) =
    stringPreferencesKey("last_opened_date_$diaryId")

    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

    private val AUTO_BACKUP_FREQUENCY = stringPreferencesKey("auto_backup_frequency")
    private val AUTO_BACKUP_FOLDER = stringPreferencesKey("auto_backup_folder")
    private val LAST_AUTO_BACKUP_TIME = longPreferencesKey("last_auto_backup_time")
    private val LAST_AUTO_BACKUP_ERROR = stringPreferencesKey("last_auto_backup_error")

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

    fun lastOpenedDate(context: Context, diaryId: Int): Flow<String?> =
        context.dataStore.data.map { prefs ->
            prefs[lastOpenedDateKey(diaryId)]
        }

    suspend fun setLastOpenedDate(context: Context, diaryId: Int, date: String) {
        context.dataStore.edit { prefs ->
            prefs[lastOpenedDateKey(diaryId)] = date
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

    /* ---------------- AUTOMATIC BACKUP ---------------- */

    fun autoBackupFrequency(context: Context): Flow<AutoBackupFrequency> =
        context.dataStore.data.map { prefs ->
            prefs[AUTO_BACKUP_FREQUENCY]
                ?.let { runCatching { AutoBackupFrequency.valueOf(it) }.getOrNull() }
                ?: AutoBackupFrequency.OFF
        }

    suspend fun setAutoBackupFrequency(context: Context, frequency: AutoBackupFrequency) {
        context.dataStore.edit { it[AUTO_BACKUP_FREQUENCY] = frequency.name }
    }

    /** The folder the user picked (a content:// tree URI), or null. */
    fun autoBackupFolder(context: Context): Flow<String?> =
        context.dataStore.data.map { it[AUTO_BACKUP_FOLDER] }

    suspend fun setAutoBackupFolder(context: Context, uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(AUTO_BACKUP_FOLDER) else prefs[AUTO_BACKUP_FOLDER] = uri
        }
    }

    data class AutoBackupStatus(val lastSuccess: Long?, val lastError: String?)

    fun autoBackupStatus(context: Context): Flow<AutoBackupStatus> =
        context.dataStore.data.map { AutoBackupStatus(it[LAST_AUTO_BACKUP_TIME], it[LAST_AUTO_BACKUP_ERROR]) }

    suspend fun recordAutoBackupResult(context: Context, time: Long, error: String?) {
        context.dataStore.edit { prefs ->
            if (error == null) {
                prefs[LAST_AUTO_BACKUP_TIME] = time
                prefs.remove(LAST_AUTO_BACKUP_ERROR)
            } else {
                prefs[LAST_AUTO_BACKUP_ERROR] = error
            }
        }
    }
}

enum class AutoBackupFrequency(val days: Long) {
    OFF(0),
    DAILY(1),
    WEEKLY(7)
}
