package com.aburv.kurippidu.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.withTransaction
import com.aburv.kurippidu.data.backup.BackupDiary
import com.aburv.kurippidu.data.backup.BackupEntry
import com.aburv.kurippidu.data.backup.BackupSet
import com.aburv.kurippidu.data.backup.BackupSettings
import com.aburv.kurippidu.data.backup.BackupTodo
import com.aburv.kurippidu.data.backup.FullBackup
import com.aburv.kurippidu.data.backup.RestoreSummary
import com.aburv.kurippidu.data.progress.EntryWorkout
import com.aburv.kurippidu.data.progress.FoodDay
import com.aburv.kurippidu.data.progress.FoodParser
import com.aburv.kurippidu.data.progress.ParsedSet
import com.aburv.kurippidu.data.progress.Side
import com.aburv.kurippidu.data.progress.WorkoutDay
import com.aburv.kurippidu.data.progress.WorkoutParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.LocalDate

/**
 * Single entry point to diary data. ViewModels talk to this, never to the DAO,
 * so multi-step changes run in one transaction and the rules live in one place.
 */
class DiaryRepository internal constructor(
    private val db: DiaryDatabase,
    /** Where photo attachments are stored (files/attachments in the app). */
    val attachmentsDir: File = File(System.getProperty("java.io.tmpdir"), "kurippidu-attachments")
) {

    private val dao = db.diaryDao()

    /* ---------------- DIARIES ---------------- */

    fun observeDiariesWithCounts(): Flow<List<DiaryWithCount>> =
        dao.observeDiariesWithCounts()

    suspend fun getDiary(diaryId: Int): DiaryOwnerEntity? = dao.getDiaryById(diaryId)

    suspend fun firstDiary(): DiaryOwnerEntity? = dao.getAllDiaries().firstOrNull()

    suspend fun getAllDiaries(): List<DiaryOwnerEntity> = dao.getAllDiaries()

    suspend fun ensureDefaultDiary() {
        if (dao.getDiaryCount() == 0) {
            dao.insertDiary(DiaryOwnerEntity(title = "My Diary"))
        }
    }

    fun observeDiary(diaryId: Int): Flow<DiaryOwnerEntity?> = dao.observeDiary(diaryId)

    suspend fun createDiary(title: String, type: DiaryType = DiaryType.PLAIN) {
        dao.insertDiary(DiaryOwnerEntity(title = title.trim().ifEmpty { type.suggestedTitle }, type = type.name))
    }

    suspend fun setDiaryType(diary: DiaryOwnerEntity, type: DiaryType) {
        dao.updateDiary(diary.copy(type = type.name))
    }

    suspend fun renameDiary(diary: DiaryOwnerEntity, newTitle: String) {
        dao.updateDiary(diary.copy(title = newTitle.trim()))
    }

    // Entries and photos rows go by CASCADE; tasks have no foreign key, so delete them here
    suspend fun deleteDiary(diary: DiaryOwnerEntity) {
        val files = dao.getAttachmentFilesForDiary(diary.diaryId)
        db.withTransaction {
            dao.deleteTodosForDiary(diary.diaryId)
            dao.deleteDiary(diary)
        }
        files.forEach { File(attachmentsDir, it).delete() }
    }

    /* ---------------- ENTRIES ---------------- */

    suspend fun getEntry(diaryId: Int, date: LocalDate): DiaryEntity? =
        dao.getEntryByDate(diaryId, date.toString())

    /** Updates the day's entry in place (keeping its id and other fields), or creates it. */
    suspend fun saveEntry(
        diaryId: Int,
        date: LocalDate,
        content: String,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val existing = dao.getEntryByDate(diaryId, date.toString())
        if (existing != null) {
            if (existing.content != content) {
                dao.updateEntry(existing.copy(content = content, updatedAt = updatedAt))
                updateTags(existing.id, content)
            }
        } else {
            val id = dao.insert(
                DiaryEntity(diaryOwnerId = diaryId, date = date.toString(), content = content, updatedAt = updatedAt)
            ).toInt()
            updateTags(id, content)
        }
    }

    /** Keeps the #tag lookup in step with the entry's text. */
    private suspend fun updateTags(entryId: Int, content: String) {
        dao.deleteTagsForEntry(entryId)
        val tags = Tags.extract(content)
        if (tags.isNotEmpty()) dao.insertTags(tags.map { EntryTagEntity(entryId, it) })
    }

    fun observeTags(diaryId: Int): Flow<List<TagCount>> = dao.observeTags(diaryId)

    suspend fun getAllEntries(diaryId: Int): List<DiaryEntity> = dao.getAllEntries(diaryId)

    suspend fun countEntries(diaryId: Int): Int = dao.getCount(diaryId)

    suspend fun hasAnyEntries(): Boolean = dao.hasAnyEntries()

    fun observeEntryDates(diaryId: Int): Flow<Set<LocalDate>> =
        dao.observeEntryDates(diaryId).map { dates ->
            dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        }

    /** Entries matching [query] (text or date); with [tag], only entries carrying that #tag. */
    suspend fun search(diaryId: Int, query: String, tag: String? = null): List<DiaryEntity> {
        // Escape LIKE wildcards so "%" or "_" typed by the user match literally
        val escaped = query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return if (tag == null) dao.searchEntries(diaryId, "%$escaped%")
        else dao.searchEntriesWithTag(diaryId, tag, "%$escaped%")
    }


    /** Writes all entries in one transaction: either every entry lands or none do. */
    suspend fun replaceEntries(
        diaryId: Int,
        entries: List<ParsedDiary>,
        clearFirst: Boolean
    ) {
        db.withTransaction {
            if (clearFirst) dao.clearDiary(diaryId)
            entries.forEach { saveEntry(diaryId, it.date, it.content) }
        }
        if (clearFirst) deleteUnusedPhotoFiles()
    }

    /* ---------------- PHOTOS ---------------- */

    fun attachmentFile(attachment: AttachmentEntity): File = File(attachmentsDir, attachment.fileName)

    suspend fun attachmentsOf(entry: DiaryEntity): List<AttachmentEntity> = dao.getAttachments(entry.id)

    fun observeAttachments(diaryId: Int, date: LocalDate): Flow<List<AttachmentEntity>> =
        dao.observeAttachments(diaryId, date.toString())

    /** Adds a photo (already copied into [attachmentsDir]) to the day, creating the entry if needed. */
    suspend fun addAttachment(diaryId: Int, date: LocalDate, fileName: String) = db.withTransaction {
        if (dao.getEntryByDate(diaryId, date.toString()) == null) saveEntry(diaryId, date, "")
        val entry = dao.getEntryByDate(diaryId, date.toString())!!
        dao.insertAttachment(AttachmentEntity(entryId = entry.id, fileName = fileName))
    }

    suspend fun deleteAttachment(attachment: AttachmentEntity) {
        dao.deleteAttachment(attachment)
        attachmentFile(attachment).delete()
    }

    /** Removes photo files no entry points to any more (after a diary is cleared). */
    suspend fun deleteUnusedPhotoFiles() {
        val used = dao.getAllAttachmentFiles().toSet()
        attachmentsDir.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }

    /* ---------------- TODOS ---------------- */

    fun observeTodosForDay(diaryId: Int, date: LocalDate): Flow<List<TodoEntity>> =
        combine(
            dao.observeTodosOnDate(diaryId, date.toString()),
            dao.observeRecurringTodosForDate(diaryId, date.toString())
        ) { onDate, recurring -> mergeTodosForDay(onDate, recurring, date) }

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

    /** @param end null = no end date; @param repeatDays weekday mask (see Weekdays), null = every day */
    suspend fun addRecurringTodo(
        diaryId: Int,
        title: String,
        start: LocalDate,
        end: LocalDate?,
        repeatDays: Int? = null
    ) {
        dao.insertTodo(
            TodoEntity(
                diaryId = diaryId,
                title = title,
                date = start.toString(), // anchor date
                type = TodoType.RECURRING,
                startDate = start.toString(),
                endDate = end?.toString(),
                repeatDays = repeatDays
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

    suspend fun updateRecurringTodo(todo: TodoEntity, start: LocalDate, end: LocalDate?, repeatDays: Int?) {
        dao.updateTodo(todo.copy(startDate = start.toString(), endDate = end?.toString(), repeatDays = repeatDays))
    }

    suspend fun updateRecurringTodoDates(todo: TodoEntity, start: LocalDate, end: LocalDate?) =
        updateRecurringTodo(todo, start, end, todo.repeatDays)

    /** Per recurring task: scheduled days in a row it was ticked. */
    suspend fun taskStreaks(diaryId: Int, today: LocalDate): Map<Int, Int> {
        val all = dao.getAllTodos(diaryId)
        val doneByParent = all.filter { it.type == TodoType.DAILY_OVERRIDE && it.isDone && it.parentId != null }
            .groupBy({ it.parentId!! }, { LocalDate.parse(it.date) })
        return all.filter { it.type == TodoType.RECURRING }
            .associate { task -> task.id to taskStreak(task, doneByParent[task.id].orEmpty().toSet(), today) }
    }

    /* ---------------- MOOD, STREAKS, MEMORIES ---------------- */

    /** Sets (or clears, with null) the day's mood, creating the entry if needed. */
    suspend fun setMood(diaryId: Int, date: LocalDate, mood: Mood?) {
        val existing = dao.getEntryByDate(diaryId, date.toString())
        if (existing != null) {
            dao.updateEntry(existing.copy(mood = mood?.key))
        } else if (mood != null) {
            dao.insert(DiaryEntity(diaryOwnerId = diaryId, date = date.toString(), content = "", mood = mood.key))
        }
    }

    /** Sets [mood], or clears it if it's already the day's mood (tap again to clear). */
    suspend fun toggleMood(diaryId: Int, date: LocalDate, mood: Mood) = db.withTransaction {
        val current = Mood.fromKey(dao.getEntryByDate(diaryId, date.toString())?.mood)
        setMood(diaryId, date, if (current == mood) null else mood)
    }

    fun observeMoods(diaryId: Int): Flow<Map<LocalDate, Mood>> =
        dao.observeMoods(diaryId).map { rows ->
            rows.mapNotNull { row ->
                val date = runCatching { LocalDate.parse(row.date) }.getOrNull()
                val mood = Mood.fromKey(row.mood)
                if (date != null && mood != null) date to mood else null
            }.toMap()
        }

    /** Writing streak for every diary, updated live. */
    fun observeWritingStreaks(today: () -> LocalDate = { LocalDate.now() }): Flow<Map<Int, Int>> =
        dao.observeWrittenDates().map { rows ->
            rows.groupBy({ it.diaryId }, { runCatching { LocalDate.parse(it.date) }.getOrNull() })
                .mapValues { (_, dates) -> writingStreak(dates.filterNotNull().toSet(), today()) }
        }

    /** What was written on this day in earlier years, newest first. */
    suspend fun onThisDay(diaryId: Int, date: LocalDate): List<DiaryEntity> =
        dao.getOnThisDay(diaryId, date.toString().substring(5), date.toString())

    suspend fun weeklySummary(diaryId: Int, today: LocalDate): WeeklySummary {
        val from = today.minusDays(6)
        return weeklySummary(
            entries = dao.getEntriesBetween(diaryId, from.toString(), today.toString()),
            todos = dao.getTodosBetween(diaryId, from.toString(), today.toString()),
            recurring = dao.getAllTodos(diaryId).filter { it.type == TodoType.RECURRING },
            today = today
        )
    }

    /* ---------------- WORKOUTS ---------------- */

    /** One day's workout: the confirmed sets if reviewed, else what the parser read. */
    suspend fun workoutFor(diaryId: Int, date: LocalDate): EntryWorkout {
        val entry = dao.getEntryByDate(diaryId, date.toString()) ?: return EntryWorkout()
        val parsed = WorkoutParser.parse(entry.content)
        val review = dao.getWorkoutReview(entry.id)
            ?: return EntryWorkout(parsed.sets, parsed.notes, confirmed = false, changedSinceReview = false)
        return EntryWorkout(
            dao.getWorkoutSets(entry.id).map { it.toParsed() }, parsed.notes,
            confirmed = true, changedSinceReview = review.contentHash != entry.content.hashCode()
        )
    }

    /** Saves the sets from the review sheet as this day's workout. */
    suspend fun confirmWorkout(diaryId: Int, date: LocalDate, sets: List<ParsedSet>) {
        val entry = dao.getEntryByDate(diaryId, date.toString()) ?: return
        db.withTransaction {
            dao.deleteWorkoutSets(entry.id)
            dao.insertWorkoutSets(sets.mapIndexed { i, set -> WorkoutSetEntity.from(entry.id, i, set) })
            dao.upsertWorkoutReview(WorkoutReviewEntity(entry.id, entry.content.hashCode()))
        }
    }

    /** Every day with a workout, oldest first: confirmed sets where reviewed, parsed ones elsewhere. */
    suspend fun workoutHistory(diaryId: Int): List<WorkoutDay> {
        val confirmed = dao.getWorkoutSetsForDiary(diaryId).groupBy { it.entryId }
        val reviewed = dao.getWorkoutReviewsForDiary(diaryId).map { it.entryId }.toSet()
        return dao.getAllEntries(diaryId).mapNotNull { entry ->
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@mapNotNull null
            if (entry.id in reviewed) {
                WorkoutDay(date, confirmed[entry.id].orEmpty().map { it.toParsed() }, confirmed = true)
            } else {
                WorkoutParser.parse(entry.content).sets.takeIf { it.isNotEmpty() }?.let { WorkoutDay(date, it, confirmed = false) }
            }
        }.filter { it.sets.isNotEmpty() }.sortedBy { it.date }
    }

    /** Every day's food, oldest first. Estimates only. */
    suspend fun foodHistory(diaryId: Int): List<Pair<LocalDate, FoodDay>> =
        dao.getAllEntries(diaryId).mapNotNull { entry ->
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@mapNotNull null
            FoodParser.parseDay(entry.content).takeIf { it.items.isNotEmpty() }?.let { date to it }
        }.sortedBy { it.first }

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
                        type = diary.type,
                        entries = dao.getAllEntries(diary.diaryId).map { entry ->
                            BackupEntry(
                                date = entry.date,
                                content = entry.content,
                                mood = entry.mood,
                                updatedAt = entry.updatedAt,
                                attachments = dao.getAttachments(entry.id).map { it.fileName },
                                workout = dao.getWorkoutReview(entry.id)?.let {
                                    dao.getWorkoutSets(entry.id).map { set ->
                                        BackupSet(set.exercise, set.side, set.weightKg, set.reps, set.sets, set.minutes, set.warmUp)
                                    }
                                }
                            )
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
                                parentKey = todo.parentId,
                                repeatDays = todo.repeatDays
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
            val sameName = dao.getDiaryByTitle(diary.title)
            // A plain diary takes the backup's type; a type chosen on this phone is kept
            if (sameName != null && sameName.diaryType == DiaryType.PLAIN && diary.type != DiaryType.PLAIN.name) {
                dao.updateDiary(sameName.copy(type = DiaryType.fromKey(diary.type).name))
            }
            val diaryId = sameName?.diaryId
                ?: dao.insertDiary(
                    DiaryOwnerEntity(
                        title = diary.title,
                        createdAt = diary.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        type = DiaryType.fromKey(diary.type).name
                    )
                ).toInt()
            diaries++

            diary.entries.forEach { entry ->
                val date = LocalDate.parse(entry.date)
                saveEntry(diaryId, date, entry.content, updatedAt = entry.updatedAt ?: System.currentTimeMillis())
                Mood.fromKey(entry.mood)?.let { setMood(diaryId, date, it) }
                entry.workout?.let { sets ->
                    confirmWorkout(diaryId, date, sets.map {
                        ParsedSet(it.exercise, Side.entries.firstOrNull { s -> s.name == it.side } ?: Side.BOTH,
                            it.weightKg, it.reps, it.sets, it.minutes, it.warmUp)
                    })
                }
                // Photos: their files were unpacked into attachmentsDir before this runs
                if (entry.attachments.isNotEmpty()) {
                    val saved = dao.getEntryByDate(diaryId, entry.date)!!
                    val already = dao.getAttachments(saved.id).map { it.fileName }.toSet()
                    entry.attachments
                        .filter { it !in already && File(attachmentsDir, it).exists() }
                        .forEach { dao.insertAttachment(AttachmentEntity(entryId = saved.id, fileName = it)) }
                }
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
                            isDone = todo.isDone, repeatDays = todo.repeatDays
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
                INSTANCE ?: DiaryRepository(
                    DiaryDatabase.getDatabase(context),
                    ImageImporter.attachmentsDir(context.applicationContext)
                ).also { INSTANCE = it }
            }

        /** Tests only: make get() return [repository] (an in-memory DB), or reset with null. */
        @VisibleForTesting
        fun setInstanceForTests(repository: DiaryRepository?) {
            synchronized(this) { INSTANCE = repository }
        }
    }
}
