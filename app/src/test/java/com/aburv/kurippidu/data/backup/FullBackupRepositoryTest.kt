package com.aburv.kurippidu.data.backup

import com.aburv.kurippidu.data.DiaryOwnerEntity
import com.aburv.kurippidu.data.DiaryRepository
import com.aburv.kurippidu.data.TodoType
import com.aburv.kurippidu.testutil.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FullBackupRepositoryTest {

    private val day = LocalDate.of(2026, 1, 5)

    /** A phone with two diaries, entries, a daily task and a recurring task ticked on one day. */
    private suspend fun fillOldPhone(repo: DiaryRepository, db: com.aburv.kurippidu.data.DiaryDatabase) {
        val gym = db.diaryDao().insertDiary(DiaryOwnerEntity(title = "Gym", createdAt = 100)).toInt()
        val food = db.diaryDao().insertDiary(DiaryOwnerEntity(title = "Food", createdAt = 200)).toInt()
        repo.saveEntry(gym, day, "Leg day")
        repo.saveEntry(gym, day.plusDays(1), "Rest")
        repo.saveEntry(food, day, "Dosa")
        repo.addDailyTodo(gym, "Buy shoes", day.plusDays(2)) // also creates an empty entry
        repo.addRecurringTodo(gym, "Stretch", day, day.plusDays(10))
        val stretch = repo.observeTodosForDay(gym, day).first().single { it.title == "Stretch" }
        repo.toggleTodo(stretch, day)
    }

    private suspend fun describe(repo: DiaryRepository, db: com.aburv.kurippidu.data.DiaryDatabase): List<String> =
        db.diaryDao().getAllDiaries().flatMap { diary ->
            val entries = repo.getAllEntries(diary.diaryId).map { "${diary.title} ${it.date} '${it.content}'" }
            val todos = db.diaryDao().getAllTodos(diary.diaryId).map { todo ->
                val parent = todo.parentId?.let { id -> db.diaryDao().getAllTodos(diary.diaryId).first { it.id == id }.title }
                "${diary.title} task ${todo.title} ${todo.type} ${todo.date} done=${todo.isDone} parent=$parent"
            }
            entries + todos
        }.sorted()

    @Test
    fun `everything comes back on a new phone`() = runBlocking {
        val oldDb = createTestDatabase()
        val oldRepo = DiaryRepository(oldDb)
        fillOldPhone(oldRepo, oldDb)
        val file = BackupCodec.encode(oldRepo.exportFullBackup(BackupSettings(), "2026-01-10T10:00:00"))
        val before = describe(oldRepo, oldDb)
        oldDb.close()

        // Uninstall + reinstall: an empty database
        val newDb = createTestDatabase()
        val newRepo = DiaryRepository(newDb)
        val summary = newRepo.restoreFullBackup((BackupCodec.decode(file) as ParsedBackup.Full).backup)

        assertEquals(before, describe(newRepo, newDb))
        assertEquals(2, summary.diaries)
        assertEquals(listOf("Gym", "Food"), newDb.diaryDao().getAllDiaries().map { it.title })

        // The recurring task's tick still belongs to that day only
        val gym = newDb.diaryDao().getAllDiaries().first { it.title == "Gym" }.diaryId
        assertTrue(newRepo.observeTodosForDay(gym, day).first().single { it.title == "Stretch" }.isDone)
        assertEquals(false, newRepo.observeTodosForDay(gym, day.plusDays(1)).first().single { it.title == "Stretch" }.isDone)
        newDb.close()
    }

    @Test
    fun `restoring the same file twice adds nothing`() = runBlocking {
        val db = createTestDatabase()
        val repo = DiaryRepository(db)
        fillOldPhone(repo, db)
        val backup = repo.exportFullBackup(BackupSettings(), "now")
        val before = describe(repo, db)

        repo.restoreFullBackup(backup)
        repo.restoreFullBackup(backup)

        assertEquals(before, describe(repo, db))
        db.close()
    }

    @Test
    fun `restore merges into a diary with the same name`() = runBlocking {
        val db = createTestDatabase()
        val repo = DiaryRepository(db)
        val gym = db.diaryDao().insertDiary(DiaryOwnerEntity(title = "Gym")).toInt()
        repo.saveEntry(gym, day, "Written on the new phone")
        repo.saveEntry(gym, day.plusDays(5), "Only here")

        repo.restoreFullBackup(
            FullBackup(
                exportedAt = "x",
                diaries = listOf(
                    BackupDiary(title = "Gym", entries = listOf(BackupEntry(date = day.toString(), content = "From backup")))
                )
            )
        )

        assertEquals(1, db.diaryDao().getDiaryCount())
        assertEquals(listOf("From backup", "Only here"), repo.getAllEntries(gym).map { it.content })
        db.close()
    }

    @Test
    fun `a failing restore changes nothing`() = runBlocking {
        val db = createTestDatabase()
        val repo = DiaryRepository(db)
        val gym = db.diaryDao().insertDiary(DiaryOwnerEntity(title = "Gym")).toInt()
        repo.saveEntry(gym, day, "Safe")

        // Second entry has an invalid date: decode would reject it, but if a bad
        // backup ever reaches the repository the transaction must roll back
        val bad = FullBackup(
            exportedAt = "x",
            diaries = listOf(
                BackupDiary(
                    title = "Gym",
                    entries = listOf(BackupEntry(day.toString(), "Changed"), BackupEntry("bad-date", "x"))
                )
            )
        )
        runCatching { repo.restoreFullBackup(bad) }

        assertEquals(listOf("Safe"), repo.getAllEntries(gym).map { it.content })
        db.close()
    }

    @Test
    fun `saving keeps the entry's id`() = runBlocking {
        val db = createTestDatabase()
        val repo = DiaryRepository(db)
        val gym = db.diaryDao().insertDiary(DiaryOwnerEntity(title = "Gym")).toInt()
        repo.saveEntry(gym, day, "One")
        val id = repo.getEntry(gym, day)!!.id
        repo.saveEntry(gym, day, "Two")
        assertEquals(id, repo.getEntry(gym, day)!!.id)
        assertEquals(TodoType.DAILY, "DAILY") // type names are part of the backup format
        db.close()
    }
}
