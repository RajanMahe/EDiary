package com.aburv.kurippidu.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aburv.kurippidu.AutoBackupFrequency
import com.aburv.kurippidu.BackupPreferences
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Writes one dated backup file into a folder and removes old automatic ones. */
class AutoBackupRunner(
    private val context: Context,
    private val service: BackupService = BackupService(context)
) {

    /** @return the name of the file written */
    suspend fun run(folder: DocumentFile, now: LocalDateTime = LocalDateTime.now()): String {
        check(folder.isDirectory && folder.canWrite()) { "Backup folder is not available" }

        val name = "$PREFIX${now.toLocalDate()}.zip"

        // One file per day: a second run the same day replaces it
        folder.findFile(name)?.delete()
        val file = folder.createFile("application/zip", name)
            ?: error("Could not create $name")
        context.contentResolver.openOutputStream(file.uri)?.use { service.writeBackupZip(it, now) }
            ?: error("Could not write $name")

        prune(folder)
        return file.name ?: name
    }

    /** Keeps the newest [KEEP] automatic backups; files the user made are never touched. */
    private fun prune(folder: DocumentFile) {
        folder.listFiles()
            // .json from older versions and .zip now; the date in the name sorts them
            .filter { it.isFile && it.name.orEmpty().let { n -> n.startsWith(PREFIX) && (n.endsWith(".zip") || n.endsWith(".json")) } }
            .sortedByDescending { it.name.orEmpty().removeSuffix(".zip").removeSuffix(".json") }
            .drop(KEEP)
            .forEach { it.delete() }
    }

    companion object {
        const val PREFIX = "kurippidu_auto_"
        const val KEEP = 7

        /** Opens the picked folder: a SAF tree URI in the app, a plain folder in tests. */
        fun folderFor(context: Context, uri: Uri): DocumentFile? =
            if (uri.scheme == "file") DocumentFile.fromFile(java.io.File(uri.path!!))
            else DocumentFile.fromTreeUri(context, uri)
    }
}

class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val folderUri = BackupPreferences.autoBackupFolder(applicationContext).first()
            ?: return fail(now, "No backup folder chosen")

        val folder = AutoBackupRunner.folderFor(applicationContext, Uri.parse(folderUri))
        if (folder == null || !folder.exists() || !folder.canWrite()) {
            // Folder deleted or permission removed: the user has to pick it again
            return fail(now, "Backup folder is no longer available. Choose it again.")
        }

        return try {
            AutoBackupRunner(applicationContext).run(folder)
            BackupPreferences.recordAutoBackupResult(applicationContext, now, error = null)
            Result.success()
        } catch (e: Exception) {
            BackupPreferences.recordAutoBackupResult(applicationContext, now, e.message ?: "Backup failed")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun fail(now: Long, message: String): Result {
        BackupPreferences.recordAutoBackupResult(applicationContext, now, message)
        return Result.failure()
    }
}

object AutoBackupScheduler {
    const val WORK_NAME = "auto_backup"

    /** Starts, changes or stops the periodic backup to match [frequency]. */
    fun apply(context: Context, frequency: AutoBackupFrequency) {
        val workManager = WorkManager.getInstance(context)
        if (frequency == AutoBackupFrequency.OFF) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(frequency.days, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
