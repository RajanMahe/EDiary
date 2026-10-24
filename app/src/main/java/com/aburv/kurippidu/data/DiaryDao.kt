package com.aburv.kurippidu.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class DateMood(val date: String, val mood: String?)

data class DiaryDate(val diaryId: Int, val date: String)

data class DiaryWithCount(
    @Embedded val diary: DiaryOwnerEntity,
    val entryCount: Int
)

@Dao
interface DiaryDao {

    /* ---------------- DIARIES ---------------- */

    @Insert
    suspend fun insertDiary(diary: DiaryOwnerEntity): Long

    @Update
    suspend fun updateDiary(diary: DiaryOwnerEntity)

    @Delete
    suspend fun deleteDiary(diary: DiaryOwnerEntity)

    @Query("SELECT COUNT(*) FROM diaries")
    suspend fun getDiaryCount(): Int

    @Query("SELECT * FROM diaries ORDER BY createdAt")
    suspend fun getAllDiaries(): List<DiaryOwnerEntity>

    @Query("SELECT * FROM diaries WHERE diaryId = :id")
    suspend fun getDiaryById(id: Int): DiaryOwnerEntity?

    @Query("SELECT * FROM diaries WHERE title = :title ORDER BY diaryId LIMIT 1")
    suspend fun getDiaryByTitle(title: String): DiaryOwnerEntity?

    // Re-emits whenever a diary or any entry changes, so counts never go stale
    @Query("""
        SELECT diaries.*,
               (SELECT COUNT(*) FROM entries WHERE entries.diaryOwnerId = diaries.diaryId) AS entryCount
        FROM diaries
        ORDER BY diaries.createdAt
    """)
    fun observeDiariesWithCounts(): Flow<List<DiaryWithCount>>

    /* ---------------- ENTRIES ---------------- */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntity)

    @Update
    suspend fun updateEntry(entry: DiaryEntity)

    @Query("SELECT * FROM entries WHERE diaryOwnerId = :diaryId AND date = :date")
    suspend fun getEntryByDate(diaryId: Int, date: String): DiaryEntity?

    @Query("SELECT * FROM entries WHERE diaryOwnerId = :diaryId ORDER BY date ASC")
    suspend fun getAllEntries(diaryId: Int): List<DiaryEntity>

    @Query("""
        SELECT * FROM entries
        WHERE diaryOwnerId = :diaryId
        AND (content LIKE :query ESCAPE '\' OR date LIKE :query ESCAPE '\')
        ORDER BY date DESC
    """)
    suspend fun searchEntries(diaryId: Int, query: String): List<DiaryEntity>

    @Query("SELECT COUNT(*) FROM entries WHERE diaryOwnerId = :diaryId")
    suspend fun getCount(diaryId: Int): Int

    @Query("SELECT EXISTS(SELECT 1 FROM entries)")
    suspend fun hasAnyEntries(): Boolean

    @Query("DELETE FROM entries WHERE diaryOwnerId = :diaryId")
    suspend fun clearDiary(diaryId: Int)

    @Query("SELECT date FROM entries WHERE diaryOwnerId = :diaryId")
    fun observeEntryDates(diaryId: Int): Flow<List<String>>

    @Query("SELECT date, mood FROM entries WHERE diaryOwnerId = :diaryId AND mood IS NOT NULL")
    fun observeMoods(diaryId: Int): Flow<List<DateMood>>

    /** Days with actual writing, across all diaries (for streaks). */
    @Query("SELECT diaryOwnerId AS diaryId, date FROM entries WHERE trim(content) != ''")
    fun observeWrittenDates(): Flow<List<DiaryDate>>

    @Query("SELECT date FROM entries WHERE diaryOwnerId = :diaryId AND trim(content) != '' AND date >= :from AND date <= :to")
    suspend fun getWrittenDatesBetween(diaryId: Int, from: String, to: String): List<String>

    /** Earlier years' entries written on the same month and day (MM-dd). */
    @Query("""
        SELECT * FROM entries
        WHERE diaryOwnerId = :diaryId
        AND substr(date, 6) = :monthDay
        AND date < :before
        AND trim(content) != ''
        ORDER BY date DESC
    """)
    suspend fun getOnThisDay(diaryId: Int, monthDay: String, before: String): List<DiaryEntity>

    @Query("SELECT * FROM entries WHERE diaryOwnerId = :diaryId AND date >= :from AND date <= :to ORDER BY date")
    suspend fun getEntriesBetween(diaryId: Int, from: String, to: String): List<DiaryEntity>

    /* ---------------- TODOS ---------------- */

    @Insert
    suspend fun insertTodo(todo: TodoEntity): Long

    @Update
    suspend fun updateTodo(todo: TodoEntity)

    @Delete
    suspend fun deleteTodo(todo: TodoEntity)

    // DAILY tasks and per-day recurring checks (DAILY_OVERRIDE) stored on this date
    @Query("SELECT * FROM todo WHERE diaryId = :diaryId AND date = :date")
    fun observeTodosOnDate(diaryId: Int, date: String): Flow<List<TodoEntity>>

    @Query("""
        SELECT * FROM todo
        WHERE diaryId = :diaryId
        AND type = 'RECURRING'
        AND startDate <= :date
        AND (endDate IS NULL OR endDate >= :date)
    """)
    fun observeRecurringTodosForDate(diaryId: Int, date: String): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todo WHERE diaryId = :diaryId AND type = 'RECURRING' ORDER BY id")
    fun observeAllRecurringTodos(diaryId: Int): Flow<List<TodoEntity>>

    @Query("""
        SELECT * FROM todo
        WHERE parentId = :parentId AND date = :date AND type = 'DAILY_OVERRIDE'
        LIMIT 1
    """)
    suspend fun getOverride(parentId: Int, date: String): TodoEntity?

    @Query("DELETE FROM todo WHERE parentId = :parentId AND type = 'DAILY_OVERRIDE'")
    suspend fun deleteOverridesForRecurringTask(parentId: Int)

    @Query("DELETE FROM todo WHERE diaryId = :diaryId")
    suspend fun deleteTodosForDiary(diaryId: Int)

    @Query("SELECT * FROM todo WHERE diaryId = :diaryId ORDER BY id")
    suspend fun getAllTodos(diaryId: Int): List<TodoEntity>

    @Query("SELECT * FROM todo WHERE diaryId = :diaryId AND date >= :from AND date <= :to")
    suspend fun getTodosBetween(diaryId: Int, from: String, to: String): List<TodoEntity>
}
