package com.aburv.kurippidu.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aburv.kurippidu.BackupPreferences
import com.aburv.kurippidu.MainActivity
import com.aburv.kurippidu.R
import com.aburv.kurippidu.data.DiaryRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object Reminders {
    const val CHANNEL_ID = "daily_reminder"
    const val EXTRA_DIARY_ID = "open_diary_id"
    internal const val KEY_DIARY_ID = "diary_id"

    fun workName(diaryId: Int) = "reminder_$diaryId"

    /** Time until the next [time]: later today, or tomorrow if it has passed. */
    fun delayUntilNext(time: LocalTime, now: LocalDateTime): Duration {
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    /** Saves the time and schedules the next reminder; null turns it off. */
    suspend fun set(context: Context, diaryId: Int, time: LocalTime?) {
        BackupPreferences.setReminderTime(context, diaryId, time)
        schedule(context, diaryId, time)
    }

    fun schedule(context: Context, diaryId: Int, time: LocalTime?, now: LocalDateTime = LocalDateTime.now()) {
        val workManager = WorkManager.getInstance(context)
        if (time == null) {
            workManager.cancelUniqueWork(workName(diaryId))
            return
        }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayUntilNext(time, now))
            .setInputData(workDataOf(KEY_DIARY_ID to diaryId))
            .build()
        workManager.enqueueUniqueWork(workName(diaryId), ExistingWorkPolicy.REPLACE, request)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Daily reminders", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "A nudge to write in your diary" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @android.annotation.SuppressLint("MissingPermission") // checked by canNotify()
    fun show(context: Context, diaryId: Int, diaryTitle: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            diaryId,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_DIARY_ID, diaryId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to write ✍️")
            .setContentText("How was your day? Add today's entry to $diaryTitle.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(workName(diaryId).hashCode(), notification)
    }
}

/** Shows one diary's reminder, then schedules tomorrow's. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val diaryId = inputData.getInt(Reminders.KEY_DIARY_ID, -1)
        if (diaryId < 0) return Result.failure()
        val time = BackupPreferences.reminderTime(applicationContext, diaryId).first() ?: return Result.success()

        val repository = DiaryRepository.get(applicationContext)
        val diary = repository.getDiary(diaryId)
        if (diary == null) {
            // Diary was deleted: stop its reminders
            Reminders.set(applicationContext, diaryId, null)
            return Result.success()
        }

        // No nudge if today's page is already written
        val writtenToday = repository.getEntry(diaryId, LocalDate.now())?.content?.isNotBlank() == true
        if (!writtenToday) Reminders.show(applicationContext, diaryId, diary.title)

        Reminders.schedule(applicationContext, diaryId, time)
        return Result.success()
    }
}
