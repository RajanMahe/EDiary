package com.aburv.kurippidu.reminders

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.aburv.kurippidu.BackupPreferences
import com.aburv.kurippidu.LockMode
import com.aburv.kurippidu.data.DiaryOwnerEntity
import com.aburv.kurippidu.testutil.TestRepository
import com.aburv.kurippidu.widget.TodayWidgetData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemindersAndWidgetTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var data: TestRepository
    private var diaryId = 0
    private val today = LocalDate.now()

    @Before
    fun setUp() {
        data = TestRepository()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        diaryId = runBlocking {
            BackupPreferences.setLockMode(context, LockMode.OFF)
            data.dao.insertDiary(DiaryOwnerEntity(title = "Gym log")).toInt()
        }
    }

    @After
    fun tearDown() = data.close()

    private fun notifications() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    private fun runWorker(id: Int = diaryId) = runBlocking {
        TestListenableWorkerBuilder<ReminderWorker>(context)
            .setInputData(workDataOf(Reminders.KEY_DIARY_ID to id))
            .build()
            .doWork()
    }

    private fun scheduled(id: Int = diaryId): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(Reminders.workName(id)).get()

    /* ---------------- REMINDERS ---------------- */

    @Test
    fun `setting a reminder schedules it and turning it off cancels`() = runBlocking {
        Reminders.set(context, diaryId, LocalTime.of(21, 0))
        assertEquals(LocalTime.of(21, 0), BackupPreferences.reminderTime(context, diaryId).first())
        assertEquals(WorkInfo.State.ENQUEUED, scheduled().single().state)

        Reminders.set(context, diaryId, null)
        assertNull(BackupPreferences.reminderTime(context, diaryId).first())
        assertEquals(WorkInfo.State.CANCELLED, scheduled().single().state)
    }

    @Test
    fun `reminder shows a notification and schedules tomorrow's`() = runBlocking {
        BackupPreferences.setReminderTime(context, diaryId, LocalTime.of(21, 0))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        val shown = notifications().single()
        assertEquals("Time to write ✍️", shadowOf(shown).contentTitle)
        assertTrue(shadowOf(shown).contentText.contains("Gym log"))
        assertEquals(WorkInfo.State.ENQUEUED, scheduled().single().state)
    }

    @Test
    fun `no reminder on a day already written`() = runBlocking {
        BackupPreferences.setReminderTime(context, diaryId, LocalTime.of(21, 0))
        data.repository.saveEntry(diaryId, today, "Done for today")

        runWorker()

        assertEquals(0, notifications().size)
        assertEquals(WorkInfo.State.ENQUEUED, scheduled().single().state) // still tomorrow
    }

    @Test
    fun `reminder for a deleted diary switches itself off`() = runBlocking {
        BackupPreferences.setReminderTime(context, 999, LocalTime.of(21, 0))
        runWorker(999)
        assertEquals(0, notifications().size)
        assertNull(BackupPreferences.reminderTime(context, 999).first())
    }

    @Test
    fun `no notification without permission`() = runBlocking {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        BackupPreferences.setReminderTime(context, diaryId, LocalTime.of(21, 0))
        runWorker()
        assertEquals(0, notifications().size)
    }

    /* ---------------- WIDGET ---------------- */

    @Test
    fun `widget shows today's tasks of the last opened diary`() = runBlocking {
        val other = data.dao.insertDiary(DiaryOwnerEntity(title = "Food")).toInt()
        data.repository.addDailyTodo(other, "Buy rice", today)
        BackupPreferences.setLastDiary(context, other)

        val state = TodayWidgetData.load(context, today)

        assertEquals("Food", state.diaryTitle)
        assertEquals(listOf("Buy rice" to false), state.tasks)
        assertEquals(false, state.writtenToday)
    }

    @Test
    fun `widget hides task names while the app is locked`() = runBlocking {
        data.repository.addDailyTodo(diaryId, "Secret task", today)
        BackupPreferences.setLastDiary(context, diaryId)
        BackupPreferences.setLockMode(context, LockMode.BIOMETRIC)

        val state = TodayWidgetData.load(context, today)

        assertTrue(state.locked)
        assertEquals(emptyList<Pair<String, Boolean>>(), state.tasks)
    }

    @Test
    fun `widget falls back to the first diary`() = runBlocking {
        BackupPreferences.setLastDiary(context, 12345)
        data.repository.saveEntry(diaryId, today, "Written")
        val state = TodayWidgetData.load(context, today)
        assertEquals("Gym log", state.diaryTitle)
        assertEquals(true, state.writtenToday)
    }
}
