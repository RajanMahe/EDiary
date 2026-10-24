package com.aburv.kurippidu.backup

import android.content.Context
import com.aburv.kurippidu.BackupPreferences
import com.aburv.kurippidu.LockMode
import com.aburv.kurippidu.ThemeMode
import com.aburv.kurippidu.data.DiaryRepository
import com.aburv.kurippidu.data.backup.BackupCodec
import com.aburv.kurippidu.data.backup.BackupSettings
import com.aburv.kurippidu.data.backup.FullBackup
import com.aburv.kurippidu.data.backup.RestoreSummary
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Builds and restores full backups, including app settings.
 * Shared by the Backup screen and the automatic backup job.
 */
class BackupService(
    private val context: Context,
    private val repository: DiaryRepository = DiaryRepository.get(context)
) {

    suspend fun createBackupJson(now: LocalDateTime = LocalDateTime.now()): String {
        val settings = BackupSettings(
            themeMode = BackupPreferences.themeMode(context).first().name,
            lockMode = BackupPreferences.lockMode(context).first().name
        )
        val backup = repository.exportFullBackup(
            settings = settings,
            exportedAt = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        return BackupCodec.encode(backup)
    }

    /** Restores diaries, entries and tasks, then the settings stored in the file. */
    suspend fun restore(backup: FullBackup): RestoreSummary {
        val summary = repository.restoreFullBackup(backup)
        backup.settings.themeMode
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?.let { BackupPreferences.setThemeMode(context, it) }
        // The lock is only switched on, never silently off, by a restore
        if (backup.settings.lockMode == LockMode.BIOMETRIC.name) {
            BackupPreferences.setLockMode(context, LockMode.BIOMETRIC)
        }
        return summary
    }
}
