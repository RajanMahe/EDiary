package com.aburv.kurippidu.backup

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aburv.kurippidu.AutoBackupFrequency
import com.aburv.kurippidu.BackupPreferences
import com.aburv.kurippidu.data.backup.BackupCodec
import com.aburv.kurippidu.data.backup.FullBackup
import com.aburv.kurippidu.data.backup.ParsedBackup
import com.aburv.kurippidu.data.backup.RestoreSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()
    private val service = BackupService(application)

    var frequency by mutableStateOf(AutoBackupFrequency.OFF)
        private set

    /** Name of the chosen folder, or null if none (or it's no longer reachable). */
    var folderName by mutableStateOf<String?>(null)
        private set

    var status by mutableStateOf(BackupPreferences.AutoBackupStatus(null, null))
        private set

    var lastManualBackup by mutableStateOf<Long?>(null)
        private set

    init {
        viewModelScope.launch { BackupPreferences.autoBackupFrequency(context).collect { frequency = it } }
        viewModelScope.launch {
            BackupPreferences.autoBackupFolder(context).collect { uri ->
                folderName = uri?.let { AutoBackupRunner.folderFor(context, Uri.parse(it))?.name ?: it }
            }
        }
        viewModelScope.launch { BackupPreferences.autoBackupStatus(context).collect { status = it } }
        viewModelScope.launch { BackupPreferences.lastBackupTime(context).collect { lastManualBackup = it } }
    }

    /* ---------------- MANUAL ---------------- */

    suspend fun exportAll(): String = withContext(Dispatchers.IO) { service.createBackupJson() }

    suspend fun recordManualBackup() = BackupPreferences.setLastBackupTime(context, System.currentTimeMillis())

    /** @throws com.aburv.kurippidu.data.backup.BackupFormatException */
    suspend fun parse(text: String): ParsedBackup = withContext(Dispatchers.Default) { BackupCodec.decode(text) }

    suspend fun restore(backup: FullBackup): RestoreSummary = service.restore(backup)

    /* ---------------- AUTOMATIC ---------------- */

    /** Saves the folder the user picked and turns daily backups on if they were off. */
    fun chooseFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        viewModelScope.launch {
            BackupPreferences.setAutoBackupFolder(context, uri.toString())
            if (frequency == AutoBackupFrequency.OFF) setFrequencyNow(AutoBackupFrequency.DAILY)
        }
    }

    fun changeFrequency(newFrequency: AutoBackupFrequency) {
        viewModelScope.launch { setFrequencyNow(newFrequency) }
    }

    private suspend fun setFrequencyNow(newFrequency: AutoBackupFrequency) {
        BackupPreferences.setAutoBackupFrequency(context, newFrequency)
        AutoBackupScheduler.apply(context, newFrequency)
    }

    /** Runs an automatic backup straight away. @return the file name, or an error message. */
    suspend fun backUpNow(): Result<String> {
        val now = System.currentTimeMillis()
        val uri = BackupPreferences.autoBackupFolder(context).first()
            ?: return Result.failure(IllegalStateException("Choose a backup folder first"))
        return runCatching {
            val folder = AutoBackupRunner.folderFor(context, Uri.parse(uri))
                ?: error("Backup folder is no longer available. Choose it again.")
            withContext(Dispatchers.IO) { AutoBackupRunner(context, service).run(folder) }
        }.also { result ->
            BackupPreferences.recordAutoBackupResult(
                context, now, result.exceptionOrNull()?.let { it.message ?: "Backup failed" }
            )
        }
    }
}
