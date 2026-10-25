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

    @Query("SELECT * FROM diaries WHERE diaryId = :id")
    fun observeDiary(id: Int): Flow<DiaryOwnerEntity?>

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
    suspend fun insert(entry: DiaryEntity): Long

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

    /* ---------------- TAGS ---------------- */

    @Query("DELETE FROM entry_tags WHERE entryId = :entryId")
    suspend fun deleteTagsForEntry(entryId: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<EntryTagEntity>)

    @Query("""
        SELECT entry_tags.tag AS tag, COUNT(*) AS count FROM entry_tags
        JOIN entries ON entries.id = entry_tags.entryId
        WHERE entries.diaryOwnerId = :diaryId
        GROUP BY entry_tags.tag
        ORDER BY count DESC, entry_tags.tag
    """)
    fun observeTags(diaryId: Int): Flow<List<TagCount>>

    @Query("""
        SELECT entries.* FROM entries
        JOIN entry_tags ON entry_tags.entryId = entries.id
        WHERE entries.diaryOwnerId = :diaryId AND entry_tags.tag = :tag
        AND (content LIKE :query ESCAPE '\' OR date LIKE :query ESCAPE '\')
        ORDER BY date DESC
    """)
    suspend fun searchEntriesWithTag(diaryId: Int, tag: String, query: String): List<DiaryEntity>

    /* ---------------- ATTACHMENTS ---------------- */

    @Insert
    suspend fun insertAttachment(attachment: AttachmentEntity): Long

    @Delete
    suspend fun deleteAttachment(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE entryId = :entryId ORDER BY createdAt, id")
    suspend fun getAttachments(entryId: Int): List<AttachmentEntity>

    @Query("""
        SELECT attachments.* FROM attachments
        JOIN entries ON entries.id = attachments.entryId
        WHERE entries.diaryOwnerId = :diaryId AND entries.date = :date
        ORDER BY attachments.createdAt, attachments.id
    """)
    fun observeAttachments(diaryId: Int, date: String): Flow<List<AttachmentEntity>>

    @Query("""
        SELECT attachments.fileName FROM attachments
        JOIN entries ON entries.id = attachments.entryId
        WHERE entries.diaryOwnerId = :diaryId
    """)
    suspend fun getAttachmentFilesForDiary(diaryId: Int): List<String>

    @Query("SELECT fileName FROM attachments")
    suspend fun getAllAttachmentFiles(): List<String>

    /* ---------------- WORKOUTS ---------------- */

    @Query("SELECT * FROM workout_sets WHERE entryId = :entryId ORDER BY position")
    suspend fun getWorkoutSets(entryId: Int): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_reviews WHERE entryId = :entryId")
    suspend fun getWorkoutReview(entryId: Int): WorkoutReviewEntity?

    @Query("""
        SELECT workout_sets.* FROM workout_sets
        JOIN entries ON entries.id = workout_sets.entryId
        WHERE entries.diaryOwnerId = :diaryId
        ORDER BY workout_sets.entryId, workout_sets.position
    """)
    suspend fun getWorkoutSetsForDiary(diaryId: Int): List<WorkoutSetEntity>

    @Query("""
        SELECT workout_reviews.* FROM workout_reviews
        JOIN entries ON entries.id = workout_reviews.entryId
        WHERE entries.diaryOwnerId = :diaryId
    """)
    suspend fun getWorkoutReviewsForDiary(diaryId: Int): List<WorkoutReviewEntity>

    @Query("DELETE FROM workout_sets WHERE entryId = :entryId")
    suspend fun deleteWorkoutSets(entryId: Int)

    @Insert
    suspend fun insertWorkoutSets(sets: List<WorkoutSetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkoutReview(review: WorkoutReviewEntity)

    /* ---------------- ENTRY ANALYSIS (cache) ---------------- */

    @Query("""
        SELECT entry_analysis.* FROM entry_analysis
        JOIN entries ON entries.id = entry_analysis.entryId
        WHERE entries.diaryOwnerId = :diaryId
    """)
    suspend fun getAnalysesForDiary(diaryId: Int): List<EntryAnalysisEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnalyses(analyses: List<EntryAnalysisEntity>)

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
