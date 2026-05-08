package com.aburv.kurippidu.data


import androidx.room.*
import com.aburv.kurippidu.TodoEntity

@Dao
interface DiaryDao {

    /* ---------------- DIARIES ---------------- */

    @Insert
    suspend fun insertDiary(diary: DiaryOwnerEntity): Long

    @Query("SELECT * FROM diaries")
    suspend fun getAllDiaries(): List<DiaryOwnerEntity>

    @Query("SELECT * FROM diaries WHERE diaryId = :id")
    suspend fun getDiaryById(id: Int): DiaryOwnerEntity?

    @Query("SELECT COUNT(*) FROM diaries")
    suspend fun getDiaryCount(): Int




    /* ---------------- ENTRIES ---------------- */



    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntity)

    @Delete
    suspend fun delete(entry: DiaryEntity)

    @Query("""
        SELECT * FROM entries 
        WHERE diaryOwnerId = :diaryId AND date = :date
    """)
    suspend fun getEntryByDate(
        diaryId: Int,
        date: String
    ): DiaryEntity?

    @Query("""
        SELECT * FROM entries 
        WHERE diaryOwnerId = :diaryId
        ORDER BY date ASC
    """)
    suspend fun getAllEntries(diaryId: Int): List<DiaryEntity>

    @Query("""
        SELECT * FROM entries
        WHERE diaryOwnerId = :diaryId
        AND (content LIKE :query OR date LIKE :query)
        ORDER BY date DESC
    """)
    suspend fun searchEntries(
        diaryId: Int,
        query: String
    ): List<DiaryEntity>

    @Query("""
        SELECT COUNT(*) FROM entries
        WHERE diaryOwnerId = :diaryId
    """)
    suspend fun getCount(diaryId: Int): Int

    @Query("DELETE FROM entries WHERE diaryOwnerId = :diaryId")
    suspend fun clearDiary(diaryId: Int)

    // FIND the last line of the interface (the closing }) and ADD BEFORE IT:

    @Delete
    suspend fun deleteDiary(diary: DiaryOwnerEntity)

    @Update
    suspend fun updateDiary(diary: DiaryOwnerEntity)

    @Query("SELECT COUNT(*) FROM entries WHERE diaryOwnerId = :diaryId")
    suspend fun getEntryCountForDiary(diaryId: Int): Int

    @Query("""
    SELECT date FROM entries
    WHERE diaryOwnerId = :diaryId
""")
    suspend fun getAllEntryDates(diaryId: Int): List<String>

    @Insert
    suspend fun insertTodo(todo: TodoEntity)


    @Update
    suspend fun updateTodo(todo: TodoEntity)

    @Query("SELECT * FROM todo WHERE diaryId = :diaryId AND date = :date")
    suspend fun getTodosForDate(
        diaryId: Int,
        date: String
    ): List<TodoEntity>

    // ADD THIS to DiaryDao:
    @Query("""
    SELECT * FROM todo 
    WHERE diaryId = :diaryId 
    AND type = 'RECURRING'
    AND startDate <= :date 
    AND endDate >= :date
""")
    suspend fun getRecurringTodosForDate(
        diaryId: Int,
        date: String
    ): List<TodoEntity>

    @Query("SELECT * FROM todo WHERE diaryId = :diaryId AND type = 'RECURRING'")
    suspend fun getAllRecurringTodos(diaryId: Int): List<TodoEntity>

    @Delete
    suspend fun deleteTodo(todo: TodoEntity)

    // ADD to DiaryDao.kt:
    @Query("""
    DELETE FROM todo 
    WHERE diaryId = :diaryId 
    AND title = :title 
    AND type = 'DAILY_OVERRIDE'
""")
    suspend fun deleteOverridesForRecurringTask(diaryId: Int, title: String)




}