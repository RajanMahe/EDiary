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
import com.aburv.kurippidu.BackupPreferences
import com.aburv.kurippidu.LockMode
import com.aburv.kurippidu.data.DiaryOwnerEntity
import com.aburv.kurippidu.data.DiaryType
import com.aburv.kurippidu.testutil.TestRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoachTipsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var data: TestRepository
    private val today = LocalDate.now()

    @Before
    fun setUp() {
        data = TestRepository()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            BackupPreferences.setLockMode(context, LockMode.OFF)
            BackupPreferences.setCoachTips(context, true)
            BackupPreferences.clearSentTipIds(context)
        }
    }

    @After
    fun tearDown() = data.close()

    private fun notifications() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    private fun runWorker() = runBlocking {
        TestListenableWorkerBuilder<CoachTipsWorker>(context).build().doWork()
    }

    /** A gym diary whose last workout was 6 days ago, and a plain diary with nothing to say. */
    private fun gymDiaryWithBreak() = runBlocking {
        val gym = data.dao.insertDiary(DiaryOwnerEntity(title = "Gym log", type = DiaryType.GYM.name)).toInt()
        data.repository.saveEntry(gym, today.minusDays(6), "Squat 20 kg 10 rep 3 set")
        data.dao.insertDiary(DiaryOwnerEntity(title = "Thoughts"))
        gym
    }

    @Test
    fun `next run is the coming Sunday evening`() {
        val saturday = LocalDateTime.of(2026, 2, 7, 10, 0)
        assertEquals(Duration.ofHours(33), CoachTips.delayUntilNext(saturday))
        val sundayLate = LocalDateTime.of(2026, 2, 8, 20, 0)
        assertEquals(Duration.ofDays(7).minusHours(1), CoachTips.delayUntilNext(sundayLate))
    }

    @Test
    fun `turning tips on schedules the run, off cancels it`() = runBlocking {
        CoachTips.setEnabled(context, true)
        val work = { WorkManager.getInstance(context).getWorkInfosForUniqueWork(CoachTips.WORK_NAME).get().single().state }
        assertEquals(WorkInfo.State.ENQUEUED, work())
        CoachTips.setEnabled(context, false)
        assertEquals(WorkInfo.State.CANCELLED, work())
    }

    @Test
    fun `one tip per diary that has something to say, never the same one twice`() {
        gymDiaryWithBreak()
        assertEquals(ListenableWorker.Result.success(), runWorker())

        val shown = notifications().single()
        assertEquals("⏰ No workout for 6 days", shadowOf(shown).contentTitle)
        assertEquals("Gym log", shadowOf(shown).contentText)

        context.getSystemService(NotificationManager::class.java).cancelAll()
        runWorker()
        assertEquals(0, notifications().size)
    }

    @Test
    fun `with the app lock on, the tip itself stays in the app`() {
        runBlocking { BackupPreferences.setLockMode(context, LockMode.BIOMETRIC) }
        gymDiaryWithBreak()
        runWorker()
        val shown = notifications().single()
        assertEquals("A new coach tip 💡", shadowOf(shown).contentTitle)
        assertTrue(shadowOf(shown).contentText.contains("Gym log"))
        assertTrue(!shadowOf(shown).contentText.contains("workout"))
    }

    @Test
    fun `nothing is sent when tips are off`() {
        runBlocking { BackupPreferences.setCoachTips(context, false) }
        gymDiaryWithBreak()
        runWorker()
        assertEquals(0, notifications().size)
    }
}
