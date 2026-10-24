package com.aburv.kurippidu.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.withTransaction
import com.aburv.kurippidu.data.backup.BackupDiary
import com.aburv.kurippidu.data.backup.BackupEntry
import com.aburv.kurippidu.data.backup.BackupSettings
import com.aburv.kurippidu.data.backup.BackupTodo
import com.aburv.kurippidu.data.backup.FullBackup
import com.aburv.kurippidu.data.backup.RestoreSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Single entry point to diary data. ViewModels talk to this, never to the DAO,
 * so multi-step changes run in one transaction and the rules live in one place.
 */
class DiaryRepository internal constructor(private val db: DiaryDatabase) {

    private val dao = db.diaryDao()

    /* ---------------- DIARIES ---------------- */

    fun observeDiariesWithCounts(): Flow<List<DiaryWithCount>> =
        dao.observeDiariesWithCounts()

    suspend fun ensureDefaultDiary() {
        if (dao.getDiaryCount() == 0) {
            dao.insertDiary(DiaryOwnerEntity(title = "My Diary"))
        }
    }

    suspend fun createDiary(title: String) {
        dao.insertDiary(DiaryOwnerEntity(title = title.trim()))
    }

    suspend fun renameDiary(diary: DiaryOwnerEntity, newTitle: String) {
        dao.updateDiary(diary.copy(title = newTitle.trim()))
    }

    // Entries are removed by the CASCADE foreign key; tasks have none, so delete them here
    suspend fun deleteDiary(diary: DiaryOwnerEntity) = db.withTransaction {
        dao.deleteTodosForDiary(diary.diaryId)
        dao.deleteDiary(diary)
    }

    /* ---------------- ENTRIES ---------------- */

    suspend fun getEntry(diaryId: Int, date: LocalDate): DiaryEntity? =
        dao.getEntryByDate(diaryId, date.toString())

    /** Updates the day's entry in place (keeping its id and other fields), or creates it. */
    suspend fun saveEntry(diaryId: Int, date: LocalDate, content: String) {
        val existing = dao.getEntryByDate(diaryId, date.toString())
        if (existing != null) {
            dao.updateEntry(existing.copy(content = content))
        } else {
            dao.insert(DiaryEntity(diaryOwnerId = diaryId, date = date.toString(), content = content))
        }
    }

    suspend fun getAllEntries(diaryId: Int): List<DiaryEntity> = dao.getAllEntries(diaryId)

    suspend fun countEntries(diaryId: Int): Int = dao.getCount(diaryId)

    fun observeEntryDates(diaryId: Int): Flow<Set<LocalDate>> =
        dao.observeEntryDates(diaryId).map { dates ->
            dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        }

    suspend fun search(diaryId: Int, query: String): List<DiaryEntity> {
        // Escape LIKE wildcards so "%" or "_" typed by the user match literally
        val escaped = query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return dao.searchEntries(diaryId, "%$escaped%")
    }

    /** Writes all entries in one transaction: either every entry lands or none do. */
    suspend fun replaceEntries(
        diaryId: Int,
        entries: List<ParsedDiary>,
        clearFirst: Boolean
    ) = db.withTransaction {
        if (clearFirst) dao.clearDiary(diaryId)
        entries.forEach { saveEntry(diaryId, it.date, it.content) }
    }

    /* ---------------- TODOS ---------------- */

    fun observeTodosForDay(diaryId: Int, date: LocalDate): Flow<List<TodoEntity>> =
        combine(
            dao.observeTodosOnDate(diaryId, date.toString()),
            dao.observeRecurringTodosForDate(diaryId, date.toString())
        ) { onDate, recurring -> mergeTodosForDay(onDate, recurring) }

    fun observeRecurringTodos(diaryId: Int): Flow<List<TodoEntity>> =
        dao.observeAllRecurringTodos(diaryId)

    suspend fun addDailyTodo(diaryId: Int, title: String, date: LocalDate) = db.withTransaction {
        // A task makes the day show up on the calendar, so make sure an entry exists
        if (dao.getEntryByDate(diaryId, date.toString()) == null) {
            saveEntry(diaryId, date, "")
        }
        dao.insertTodo(
            TodoEntity(diaryId = diaryId, title = title, date = date.toString(), type = TodoType.DAILY)
        )
    }

    suspend fun addRecurringTodo(diaryId: Int, title: String, start: LocalDate, end: LocalDate) {
        dao.insertTodo(
            TodoEntity(
                diaryId = diaryId,
                title = title,
                date = start.toString(), // anchor date
                type = TodoType.RECURRING,
                startDate = start.toString(),
                endDate = end.toString()
            )
        )
    }

    /**
     * Daily tasks flip their own isDone. Recurring tasks store the checked state
     * per day in a DAILY_OVERRIDE row linked by parentId.
     */
    suspend fun toggleTodo(todo: TodoEntity, date: LocalDate) {
        when (todo.type) {
            TodoType.RECURRING -> {
                val existing = dao.getOverride(todo.id, date.toString())
                if (existing != null) {
                    dao.updateTodo(existing.copy(isDone = !existing.isDone))
                } else {
                    dao.insertTodo(
                        TodoEntity(
                            diaryId = todo.diaryId,
                            title = todo.title,
                            date = date.toString(),
                            type = TodoType.DAILY_OVERRIDE,
                            startDate = todo.startDate,
                            endDate = todo.endDate,
                            isDone = true,
                            parentId = todo.id
                        )
                    )
                }
            }
            else -> dao.updateTodo(todo.copy(isDone = !todo.isDone))
        }
    }

    suspend fun deleteTodo(todo: TodoEntity) = dao.deleteTodo(todo)

    suspend fun deleteRecurringTodo(todo: TodoEntity) = db.withTransaction {
        dao.deleteOverridesForRecurringTask(todo.id)
        dao.deleteTodo(todo)
    }

    suspend fun updateRecurringTodoDates(todo: TodoEntity, start: LocalDate, end: LocalDate) {
        dao.updateTodo(todo.copy(startDate = start.toString(), endDate = end.toString()))
    }

    /* ---------------- FULL BACKUP ---------------- */

    /** Everything in the database, ready to be written as a backup file. */
    suspend fun exportFullBackup(settings: BackupSettings, exportedAt: String): FullBackup =
        db.withTransaction {
            FullBackup(
                exportedAt = exportedAt,
                settings = settings,
                diaries = dao.getAllDiaries().map { diary ->
                    BackupDiary(
                        title = diary.title,
                        createdAt = diary.createdAt,
                        entries = dao.getAllEntries(diary.diaryId).map { entry ->
                            BackupEntry(date = entry.date, content = entry.content)
                        },
                        todos = dao.getAllTodos(diary.diaryId).map { todo ->
                            BackupTodo(
                                key = todo.id,
                                title = todo.title,
                                date = todo.date,
                                type = todo.type,
                                startDate = todo.startDate,
                                endDate = todo.endDate,
                                time = todo.time,
                                isDone = todo.isDone,
                                parentKey = todo.parentId
                            )
                        }
                    )
                }
            )
        }

    /**
     * Restores a full backup in one transaction (all or nothing).
     *
     * Merges instead of wiping: a diary with the same name is reused, a day
     * that already has an entry gets the backup's text, and tasks that already
     * exist are not added twice. Restoring the same file again changes nothing.
     */
    suspend fun restoreFullBackup(backup: FullBackup): RestoreSummary = db.withTransaction {
        var diaries = 0
        var entries = 0
        var todos = 0

        backup.diaries.forEach { diary ->
            val diaryId = dao.getDiaryByTitle(diary.title)?.diaryId
                ?: dao.insertDiary(
                    DiaryOwnerEntity(
                        title = diary.title,
                        createdAt = diary.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
                    )
                ).toInt()
            diaries++

            diary.entries.forEach { entry ->
                saveEntry(diaryId, LocalDate.parse(entry.date), entry.content)
                entries++
            }

            val existing = dao.getAllTodos(diaryId).toMutableList()
            val idForKey = mutableMapOf<Int, Int>()

            // Tasks first, then the per-day checks that point at them
            val (checks, tasks) = diary.todos.partition { it.parentKey != null }
            tasks.forEach { todo ->
                val same = existing.firstOrNull {
                    it.parentId == null && it.title == todo.title && it.type == todo.type &&
                            it.date == todo.date && it.startDate == todo.startDate && it.endDate == todo.endDate
                }
                val id = if (same != null) {
                    if (same.isDone != todo.isDone) dao.updateTodo(same.copy(isDone = todo.isDone))
                    same.id
                } else {
                    todos++
                    dao.insertTodo(
                        TodoEntity(
                            diaryId = diaryId, title = todo.title, date = todo.date, type = todo.type,
                            startDate = todo.startDate, endDate = todo.endDate, time = todo.time,
                            isDone = todo.isDone
                        )
                    ).toInt().also { newId ->
                        existing += TodoEntity(
                            id = newId, diaryId = diaryId, title = todo.title, date = todo.date,
                            type = todo.type, startDate = todo.startDate, endDate = todo.endDate
                        )
                    }
                }
                idForKey[todo.key] = id
            }
            checks.forEach { check ->
                val parentId = idForKey.getValue(check.parentKey!!)
                val same = existing.firstOrNull { it.parentId == parentId && it.date == check.date }
                if (same != null) {
                    if (same.isDone != check.isDone) dao.updateTodo(same.copy(isDone = check.isDone))
                } else {
                    todos++
                    dao.insertTodo(
                        TodoEntity(
                            diaryId = diaryId, title = check.title, date = check.date, type = check.type,
                            startDate = check.startDate, endDate = check.endDate,
                            isDone = check.isDone, parentId = parentId
                        )
                    )
                }
            }
        }
        RestoreSummary(diaries = diaries, entries = entries, todos = todos)
    }

    companion object {
        @Volatile
        private var INSTANCE: DiaryRepository? = null

        fun get(context: Context): DiaryRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DiaryRepository(DiaryDatabase.getDatabase(context)).also { INSTANCE = it }
            }

        /** Tests only: make get() return [repository] (an in-memory DB), or reset with null. */
        @VisibleForTesting
        fun setInstanceForTests(repository: DiaryRepository?) {
            synchronized(this) { INSTANCE = repository }
        }
    }
}
