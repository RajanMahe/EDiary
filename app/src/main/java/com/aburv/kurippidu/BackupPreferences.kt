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
private fun lastSearchKey(diaryId: Int) = stringPreferencesKey("last_search_$diaryId")
    private fun lastOpenedDateKey(diaryId: Int) =
    stringPreferencesKey("last_opened_date_$diaryId")

    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    private val SPEECH_LANGUAGE = stringPreferencesKey("speech_language")

    private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    private val COACH_TIPS = booleanPreferencesKey("coach_tips_weekly")
    private val SENT_TIP_IDS = stringSetPreferencesKey("coach_tips_sent")
    private val FONT_SIZE = stringPreferencesKey("font_size")

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

    /* ---------------- SEARCH ---------------- */

    fun lastSearch(context: Context, diaryId: Int): Flow<String> =
        context.dataStore.data.map { it[lastSearchKey(diaryId)].orEmpty() }

    suspend fun setLastSearch(context: Context, diaryId: Int, query: String) {
        context.dataStore.edit { prefs ->
            if (query.isBlank()) prefs.remove(lastSearchKey(diaryId)) else prefs[lastSearchKey(diaryId)] = query
        }
    }

    /* ---------------- REMINDERS ---------------- */

    private fun reminderKey(diaryId: Int) = stringPreferencesKey("reminder_$diaryId")

    /** Daily reminder time for a diary, or null if off. */
    fun reminderTime(context: Context, diaryId: Int): Flow<java.time.LocalTime?> =
        context.dataStore.data.map { prefs ->
            prefs[reminderKey(diaryId)]?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
        }

    suspend fun setReminderTime(context: Context, diaryId: Int, time: java.time.LocalTime?) {
        context.dataStore.edit { prefs ->
            if (time == null) prefs.remove(reminderKey(diaryId)) else prefs[reminderKey(diaryId)] = time.toString()
        }
    }

    /* ---------------- LAST DIARY (widget) ---------------- */

    private val LAST_DIARY = intPreferencesKey("last_diary")

    fun lastDiary(context: Context): Flow<Int?> = context.dataStore.data.map { it[LAST_DIARY] }

    suspend fun setLastDiary(context: Context, diaryId: Int) {
        context.dataStore.edit { it[LAST_DIARY] = diaryId }
    }

    /* ---------------- FONT SIZE ---------------- */

    fun fontSize(context: Context): Flow<DiaryFontSize> =
        context.dataStore.data.map { prefs ->
            prefs[FONT_SIZE]?.let { runCatching { DiaryFontSize.valueOf(it) }.getOrNull() } ?: DiaryFontSize.MEDIUM
        }

    suspend fun setFontSize(context: Context, size: DiaryFontSize) {
        context.dataStore.edit { it[FONT_SIZE] = size.name }
    }

    /* ---------------- SPEAK TO WRITE ---------------- */

    /** The language for the mic button; null until the user has picked one. */
    fun speechLanguage(context: Context): Flow<SpeechLanguage?> =
        context.dataStore.data.map { SpeechLanguage.fromKey(it[SPEECH_LANGUAGE]) }

    suspend fun setSpeechLanguage(context: Context, language: SpeechLanguage) {
        context.dataStore.edit { it[SPEECH_LANGUAGE] = language.name }
    }

    @androidx.annotation.VisibleForTesting
    suspend fun clearSpeechLanguage(context: Context) {
        context.dataStore.edit { it.remove(SPEECH_LANGUAGE) }
    }

    /* ---------------- COACH TIPS ---------------- */

    fun coachTips(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[COACH_TIPS] ?: false }

    suspend fun setCoachTips(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[COACH_TIPS] = enabled }
    }

    /** Tips already sent as notifications, as "diaryId:tipId". */
    fun sentTipIds(context: Context): Flow<Set<String>> =
        context.dataStore.data.map { it[SENT_TIP_IDS].orEmpty() }

    @androidx.annotation.VisibleForTesting
    suspend fun clearSentTipIds(context: Context) {
        context.dataStore.edit { it.remove(SENT_TIP_IDS) }
    }

    /** A few ids a week per diary, so the set stays small. */
    suspend fun addSentTipIds(context: Context, ids: Set<String>) {
        context.dataStore.edit { prefs -> prefs[SENT_TIP_IDS] = prefs[SENT_TIP_IDS].orEmpty() + ids }
    }

    /* ---------------- WELCOME ---------------- */

    fun onboardingDone(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone(context: Context, done: Boolean = true) {
        context.dataStore.edit { it[ONBOARDING_DONE] = done }
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
