package com.aburv.kurippidu.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Single entry point to diary data. ViewModels talk to this, never to the DAO,
 * so multi-step changes run in one transaction and the rules live in one place.
 */
class DiaryRepository private constructor(private val db: DiaryDatabase) {

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

    suspend fun saveEntry(diaryId: Int, date: LocalDate, content: String) {
        dao.insert(DiaryEntity(diaryOwnerId = diaryId, date = date.toString(), content = content))
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

    companion object {
        @Volatile
        private var INSTANCE: DiaryRepository? = null

        fun get(context: Context): DiaryRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DiaryRepository(DiaryDatabase.getDatabase(context)).also { INSTANCE = it }
            }
    }
}
