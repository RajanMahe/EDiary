package com.aburv.kurippidu.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryRepositoryTest {

    private lateinit var db: DiaryDatabase
    private lateinit var repo: DiaryRepository

    private val diaryId = 1
    private val day = LocalDate.of(2026, 1, 5)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = DiaryRepository(db)
        runBlocking { db.diaryDao().insertDiary(DiaryOwnerEntity(diaryId = diaryId, title = "Test")) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun tasksOn(date: LocalDate) = repo.observeTodosForDay(diaryId, date).first()

    @Test
    fun `checking a recurring task affects only that day`() = runBlocking {
        repo.addRecurringTodo(diaryId, "Gym", day.minusDays(4), day.plusDays(10))

        repo.toggleTodo(tasksOn(day).single(), day)

        assertTrue(tasksOn(day).single().isDone)
        assertFalse(tasksOn(day.plusDays(1)).single().isDone)

        // Unchecking flips the same day's check back
        repo.toggleTodo(tasksOn(day).single(), day)
        assertFalse(tasksOn(day).single().isDone)
    }

    @Test
    fun `same-named recurring tasks stay independent`() = runBlocking {
        repo.addRecurringTodo(diaryId, "Gym", day, day.plusDays(10))
        repo.addRecurringTodo(diaryId, "Gym", day, day.plusDays(10))

        repo.toggleTodo(tasksOn(day).first(), day)
        assertEquals(listOf(true, false), tasksOn(day).map { it.isDone })

        // Deleting the second task keeps the first task's check
        val second = repo.observeRecurringTodos(diaryId).first().last()
        repo.deleteRecurringTodo(second)

        val remaining = tasksOn(day)
        assertEquals(1, remaining.size)
        assertTrue(remaining.single().isDone)
    }

    @Test
    fun `daily task can be added and deleted`() = runBlocking {
        repo.addDailyTodo(diaryId, "Call mum", day)
        val task = tasksOn(day).single()
        assertEquals("Call mum", task.title)

        // Adding a task also marks the day on the calendar
        assertTrue(day in repo.observeEntryDates(diaryId).first())

        repo.deleteTodo(task)
        assertEquals(emptyList<TodoEntity>(), tasksOn(day))
    }

    @Test
    fun `deleting a diary also deletes its tasks`() = runBlocking {
        repo.addDailyTodo(diaryId, "Call mum", day)
        repo.addRecurringTodo(diaryId, "Gym", day, day.plusDays(3))

        val diary = db.diaryDao().observeDiariesWithCounts().first().single().diary
        repo.deleteDiary(diary)

        assertEquals(emptyList<TodoEntity>(), tasksOn(day))
        assertEquals(emptyList<TodoEntity>(), repo.observeRecurringTodos(diaryId).first())
    }

    @Test
    fun `entry counts update after saving`() = runBlocking {
        repo.saveEntry(diaryId, day, "Hello")
        repo.saveEntry(diaryId, day.plusDays(1), "Again")
        // Saving the same day again replaces it instead of adding a second entry
        repo.saveEntry(diaryId, day, "Hello, edited")

        val counts = db.diaryDao().observeDiariesWithCounts().first()
        assertEquals(2, counts.single().entryCount)
        assertEquals("Hello, edited", repo.getEntry(diaryId, day)?.content)
    }

    @Test
    fun `search treats percent and underscore literally`() = runBlocking {
        repo.saveEntry(diaryId, day, "Finished 100% of the plan")
        repo.saveEntry(diaryId, day.plusDays(1), "Finished 100 pages")
        repo.saveEntry(diaryId, day.plusDays(2), "my_file saved")
        repo.saveEntry(diaryId, day.plusDays(3), "myXfile saved")

        assertEquals(listOf("Finished 100% of the plan"), repo.search(diaryId, "100%").map { it.content })
        assertEquals(listOf("my_file saved"), repo.search(diaryId, "my_file").map { it.content })
    }

    @Test
    fun `replaceEntries can clear the diary first`() = runBlocking {
        repo.saveEntry(diaryId, day, "Old")
        repo.replaceEntries(
            diaryId,
            listOf(ParsedDiary(day.plusDays(1), "From backup")),
            clearFirst = true
        )

        assertEquals(listOf("From backup"), repo.getAllEntries(diaryId).map { it.content })
    }
}
