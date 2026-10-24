package com.aburv.kurippidu.data

import com.aburv.kurippidu.data.backup.BackupSettings
import com.aburv.kurippidu.testutil.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabitsRepositoryTest {

    private lateinit var db: DiaryDatabase
    private lateinit var repo: DiaryRepository
    private var diaryId = 0
    private val monday = LocalDate.of(2026, 1, 5)

    @Before
    fun setUp() = runBlocking {
        db = createTestDatabase()
        repo = DiaryRepository(db)
        diaryId = db.diaryDao().insertDiary(DiaryOwnerEntity(title = "Daily")).toInt()
    }

    @Test
    fun `mood can be set on an empty day, changed and cleared`() = runBlocking {
        repo.setMood(diaryId, monday, Mood.GOOD)
        assertEquals(mapOf(monday to Mood.GOOD), repo.observeMoods(diaryId).first())
        assertEquals("", repo.getEntry(diaryId, monday)!!.content)

        repo.setMood(diaryId, monday, Mood.SAD)
        assertEquals(Mood.SAD, repo.observeMoods(diaryId).first()[monday])

        repo.setMood(diaryId, monday, null)
        assertEquals(emptyMap<LocalDate, Mood>(), repo.observeMoods(diaryId).first())
    }

    @Test
    fun `saving text keeps the mood`() = runBlocking {
        repo.setMood(diaryId, monday, Mood.GREAT)
        repo.saveEntry(diaryId, monday, "Lovely day")
        assertEquals(Mood.GREAT.key, repo.getEntry(diaryId, monday)!!.mood)
    }

    @Test
    fun `on this day finds earlier years only, newest first`() = runBlocking {
        repo.saveEntry(diaryId, LocalDate.of(2024, 1, 5), "Two years ago")
        repo.saveEntry(diaryId, LocalDate.of(2025, 1, 5), "Last year")
        repo.saveEntry(diaryId, LocalDate.of(2025, 1, 6), "Wrong day")
        repo.saveEntry(diaryId, LocalDate.of(2023, 1, 5), "   ") // blank
        repo.saveEntry(diaryId, monday, "Today")

        assertEquals(listOf("Last year", "Two years ago"), repo.onThisDay(diaryId, monday).map { it.content })
    }

    @Test
    fun `writing streaks per diary update live`() = runBlocking {
        val other = db.diaryDao().insertDiary(DiaryOwnerEntity(title = "Other")).toInt()
        repo.saveEntry(diaryId, monday, "a")
        repo.saveEntry(diaryId, monday.minusDays(1), "b")
        repo.setMood(diaryId, monday.minusDays(2), Mood.OKAY) // mood only doesn't count
        repo.saveEntry(other, monday.minusDays(1), "c")

        val streaks = repo.observeWritingStreaks { monday }.first()
        assertEquals(2, streaks[diaryId])
        assertEquals(1, streaks[other])
    }

    @Test
    fun `weekday-only and open-ended recurring tasks`() = runBlocking {
        val mask = Weekdays.maskOf(setOf(DayOfWeek.MONDAY))
        repo.addRecurringTodo(diaryId, "Gym", monday, end = null, repeatDays = mask)

        assertEquals(listOf("Gym"), repo.observeTodosForDay(diaryId, monday).first().map { it.title })
        assertEquals(emptyList<String>(), repo.observeTodosForDay(diaryId, monday.plusDays(1)).first().map { it.title })
        // Two years later, still a Monday task
        val farMonday = monday.plusWeeks(104)
        assertEquals(listOf("Gym"), repo.observeTodosForDay(diaryId, farMonday).first().map { it.title })
    }

    @Test
    fun `task streaks`() = runBlocking {
        repo.addRecurringTodo(diaryId, "Read", monday.minusDays(3), end = null)
        val task = repo.observeRecurringTodos(diaryId).first().single()
        listOf(1L, 2L).forEach { repo.toggleTodo(task, monday.minusDays(it)) }

        assertEquals(mapOf(task.id to 2), repo.taskStreaks(diaryId, monday))
    }

    @Test
    fun `weekly summary from the database`() = runBlocking {
        repo.saveEntry(diaryId, monday, "one two")
        repo.setMood(diaryId, monday, Mood.GOOD)
        repo.addDailyTodo(diaryId, "Call", monday)

        val summary = repo.weeklySummary(diaryId, monday)
        assertEquals(1, summary.daysWritten)
        assertEquals(2, summary.words)
        assertEquals(1, summary.tasksTotal)
        assertEquals(Mood.GOOD, summary.topMood)
    }

    @Test
    fun `backup keeps mood, weekday repeats and open ends`() = runBlocking {
        repo.saveEntry(diaryId, monday, "Hi")
        repo.setMood(diaryId, monday, Mood.LOW)
        val mask = Weekdays.maskOf(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        repo.addRecurringTodo(diaryId, "Hike", monday, end = null, repeatDays = mask)
        val backup = repo.exportFullBackup(BackupSettings(), "now")

        val fresh = DiaryRepository(createTestDatabase())
        fresh.restoreFullBackup(backup)
        val id = fresh.firstDiary()!!.diaryId

        assertEquals(Mood.LOW, fresh.observeMoods(id).first()[monday])
        val hike = fresh.observeRecurringTodos(id).first().single()
        assertEquals(mask, hike.repeatDays)
        assertNull(hike.endDate)
        assertTrue(fresh.getEntry(id, monday)!!.updatedAt != null)
    }
}
