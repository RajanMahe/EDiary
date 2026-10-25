package com.aburv.kurippidu.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aburv.kurippidu.BackupPreferences
import com.aburv.kurippidu.LockMode
import com.aburv.kurippidu.MainActivity
import com.aburv.kurippidu.R
import com.aburv.kurippidu.data.DiaryOwnerEntity
import com.aburv.kurippidu.data.DiaryRepository
import com.aburv.kurippidu.data.coach.CoachInput
import com.aburv.kurippidu.data.coach.CoachRules
import com.aburv.kurippidu.data.coach.CoachTip
import com.aburv.kurippidu.data.diaryType
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Once a week (Sunday evening), the most useful new tip for each diary. */
object CoachTips {
    const val CHANNEL_ID = "coach_tips"
    const val WORK_NAME = "coach_tips_weekly"
    val SEND_AT: LocalTime = LocalTime.of(19, 0)

    /** Time until the next Sunday at [SEND_AT]. */
    fun delayUntilNext(now: LocalDateTime): Duration {
        var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(SEND_AT)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        return Duration.between(now, next)
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        BackupPreferences.setCoachTips(context, enabled)
        schedule(context, enabled)
    }

    fun schedule(context: Context, enabled: Boolean, now: LocalDateTime = LocalDateTime.now()) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = OneTimeWorkRequestBuilder<CoachTipsWorker>().setInitialDelay(delayUntilNext(now)).build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** All of a diary's current tips, most important first. */
    suspend fun tipsFor(repository: DiaryRepository, diary: DiaryOwnerEntity, today: LocalDate): List<CoachTip> {
        val entries = repository.getAllEntries(diary.diaryId).mapNotNull { entry ->
            runCatching { LocalDate.parse(entry.date) }.getOrNull()?.let { it to entry.content }
        }
        return CoachRules.tips(
            CoachInput(
                diary.diaryType, today,
                workouts = repository.workoutHistory(diary.diaryId),
                food = repository.foodHistory(diary.diaryId),
                entries = entries
            )
        )
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Weekly coach tips", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "One tip a week from your workout, rehab and food diaries" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * While the app lock is on, the notification only says there's a tip:
     * health details stay inside the app.
     */
    @android.annotation.SuppressLint("MissingPermission") // checked by canNotify()
    fun show(context: Context, diary: DiaryOwnerEntity, tip: CoachTip, locked: Boolean) {
        if (!Reminders.canNotify(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            diary.diaryId + 50_000,
            Intent(context, MainActivity::class.java)
                .putExtra(Reminders.EXTRA_DIARY_ID, diary.diaryId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setAutoCancel(true)
        if (locked) {
            builder.setContentTitle("A new coach tip 💡")
                .setContentText("Open ${diary.title} to read this week's tip.")
        } else {
            builder.setContentTitle("${tip.emoji} ${tip.title}")
                .setContentText(diary.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${tip.body}\n\n${diary.title} · Progress"))
        }
        NotificationManagerCompat.from(context).notify("coach_${diary.diaryId}".hashCode(), builder.build())
    }
}

/** Sends this week's tips, then schedules next Sunday. */
class CoachTipsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!BackupPreferences.coachTips(context).first()) return Result.success()

        val repository = DiaryRepository.get(context)
        val locked = BackupPreferences.lockMode(context).first() != LockMode.OFF
        val alreadySent = BackupPreferences.sentTipIds(context).first()
        val sentNow = mutableSetOf<String>()
        val today = LocalDate.now()

        repository.getAllDiaries().forEach { diary ->
            val tip = CoachTips.tipsFor(repository, diary, today).firstOrNull { "${diary.diaryId}:${it.id}" !in alreadySent }
                ?: return@forEach
            CoachTips.show(context, diary, tip, locked)
            sentNow += "${diary.diaryId}:${tip.id}"
        }
        if (sentNow.isNotEmpty()) BackupPreferences.addSentTipIds(context, sentNow)

        CoachTips.schedule(context, enabled = true)
        return Result.success()
    }
}
