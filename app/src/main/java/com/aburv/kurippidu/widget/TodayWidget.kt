package com.aburv.kurippidu.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.aburv.kurippidu.BackupPreferences
import com.aburv.kurippidu.LockMode
import com.aburv.kurippidu.MainActivity
import com.aburv.kurippidu.data.DiaryRepository
import com.aburv.kurippidu.reminders.Reminders
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** What the widget shows. Task names are left out while the app lock is on. */
data class TodayWidgetState(
    val diaryId: Int?,
    val diaryTitle: String,
    val locked: Boolean,
    val tasks: List<Pair<String, Boolean>>, // title, done
    val writtenToday: Boolean
)

object TodayWidgetData {

    /** Today's tasks from the diary opened most recently (or the first diary). */
    suspend fun load(context: Context, today: LocalDate = LocalDate.now()): TodayWidgetState {
        val repository = DiaryRepository.get(context)
        val locked = BackupPreferences.lockMode(context).first() == LockMode.BIOMETRIC

        val diary = BackupPreferences.lastDiary(context).first()?.let { repository.getDiary(it) }
            ?: repository.firstDiary()
            ?: return TodayWidgetState(null, "Kurippidu", locked, emptyList(), false)

        val tasks = if (locked) emptyList()
        else repository.observeTodosForDay(diary.diaryId, today).first().map { it.title to it.isDone }
        val written = repository.getEntry(diary.diaryId, today)?.content?.isNotBlank() == true

        return TodayWidgetState(diary.diaryId, diary.title, locked, tasks, written)
    }

    /** Redraws every placed widget; safe to call when none are placed. */
    suspend fun refresh(context: Context) {
        runCatching { TodayWidget().updateAll(context) }
    }
}

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = TodayWidgetData.load(context)
        provideContent {
            GlanceTheme { WidgetContent(context, state) }
        }
    }

    @Composable
    private fun WidgetContent(context: Context, state: TodayWidgetState) {
        val open = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                state.diaryId?.let { putExtra(Reminders.EXTRA_DIARY_ID, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        val text = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(open)
        ) {
            Text(
                "📔 ${state.diaryTitle}",
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.height(6.dp))

            when {
                state.locked -> Text("🔒 Open to see today's tasks", style = text)
                state.tasks.isEmpty() -> Text("No tasks today", style = text)
                else -> state.tasks.take(4).forEach { (title, done) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (done) "☑ " else "☐ ", style = text)
                        Text(title, style = text, maxLines = 1)
                    }
                }
            }
            if (!state.locked && state.tasks.size > 4) {
                Text("+${state.tasks.size - 4} more", style = text)
            }

            Spacer(GlanceModifier.defaultWeight())
            Button(
                text = if (state.writtenToday) "✍️ Keep writing" else "✍️ Write",
                onClick = open,
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
