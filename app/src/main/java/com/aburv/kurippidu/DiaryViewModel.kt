package com.aburv.kurippidu

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aburv.kurippidu.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DiaryRepository.get(application)

    /* ---------------- ACTIVE DIARY ---------------- */

    var activeDiaryId by mutableStateOf(0)
        private set

    var isReady by mutableStateOf(false)
        private set

    // null until setActiveDiary() is called from navigation
    private val activeDiaryFlow = MutableStateFlow<Int?>(null)
    private val todoDateFlow = MutableStateFlow(LocalDate.now())

    /* ---------------- UI STATE ---------------- */

    var diaryText by mutableStateOf(TextFieldValue(""))
        private set

    var currentDate by mutableStateOf(LocalDate.now())

    enum class SaveState { IDLE, SAVED, SAVING, DIRTY }

    var saveState by mutableStateOf(SaveState.SAVED)
        private set

    var isEditMode by mutableStateOf(false)
        private set

    fun enableEditMode() {
        isEditMode = true
    }

    fun updateEditModeForDate(date: LocalDate) {
        isEditMode = date == LocalDate.now()
    }

    /* ---------------- OBSERVED DATA ---------------- */
    // These follow Room, so they refresh by themselves after any change.

    var todos by mutableStateOf<List<TodoEntity>?>(null)  // null = not yet loaded
        private set

    var recurringTodos by mutableStateOf<List<TodoEntity>>(emptyList())
        private set

    var entryDates by mutableStateOf<Set<LocalDate>>(emptySet())
        private set

    init {
        viewModelScope.launch {
            // Only ensure at least one diary exists in DB.
            repository.ensureDefaultDiary()
        }
        viewModelScope.launch {
            activeDiaryFlow.filterNotNull()
                .flatMapLatest { repository.observeEntryDates(it) }
                .collect { entryDates = it }
        }
        viewModelScope.launch {
            activeDiaryFlow.filterNotNull()
                .flatMapLatest { repository.observeRecurringTodos(it) }
                .collect { recurringTodos = it }
        }
        viewModelScope.launch {
            // flatMapLatest drops the previous day's query, so fast date
            // switching can never show another day's tasks
            combine(activeDiaryFlow.filterNotNull(), todoDateFlow) { id, date -> id to date }
                .flatMapLatest { (id, date) -> repository.observeTodosForDay(id, date) }
                .collect { todos = it }
        }
    }

    fun setActiveDiary(id: Int) {
        // Guard: if already set to same diary, don't reload
        if (activeDiaryId == id && isReady) return
        activeDiaryId = id
        isReady = true
        activeDiaryFlow.value = id
        loadDiaryForDate(currentDate)
    }

    /* ---------------- To-Do ---------------- */

    fun loadTodos(date: LocalDate) {
        if (todoDateFlow.value != date) {
            todos = null
            todoDateFlow.value = date
        }
    }

    fun addTodo(text: String, date: LocalDate) {
        viewModelScope.launch { repository.addDailyTodo(activeDiaryId, text, date) }
    }

    fun addRecurringTodo(title: String, startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            repository.addRecurringTodo(activeDiaryId, title, startDate, endDate)
        }
    }

    fun toggleTodo(todo: TodoEntity, date: LocalDate = currentDate) {
        viewModelScope.launch { repository.toggleTodo(todo, date) }
    }

    fun deleteTodo(todo: TodoEntity) {
        viewModelScope.launch { repository.deleteTodo(todo) }
    }

    fun deleteRecurringTodo(todo: TodoEntity) {
        viewModelScope.launch { repository.deleteRecurringTodo(todo) }
    }

    fun updateRecurringTodoDates(todo: TodoEntity, newStart: LocalDate, newEnd: LocalDate) {
        viewModelScope.launch { repository.updateRecurringTodoDates(todo, newStart, newEnd) }
    }

    /* ---------------- LOAD ---------------- */

    private var loadTextJob: Job? = null

    fun loadDiaryForDate(date: LocalDate) {
        currentDate = date

        // Cancel the previous day's load so a slow result can't overwrite this day
        loadTextJob?.cancel()
        loadTextJob = viewModelScope.launch {
            val diaryId = activeDiaryFlow.filterNotNull().first()
            val entry = repository.getEntry(diaryId, date)
            if (currentDate == date) {
                diaryText = TextFieldValue(entry?.content ?: "")
            }
        }
    }

    /* ---------------- SAVE ---------------- */

    private var autosaveJob: Job? = null

    fun updateDiaryText(newValue: TextFieldValue) {
        diaryText = newValue
        saveState = SaveState.DIRTY
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(800)
            saveCurrent()
        }
    }

    private suspend fun saveCurrent() {
        if (saveState != SaveState.DIRTY || !isReady) return

        // Snapshot before suspending: the date or text may change while saving
        val diaryId = activeDiaryId
        val date = currentDate
        val text = diaryText.text

        saveState = SaveState.SAVING
        repository.saveEntry(diaryId, date, text)

        // If the user typed during the save, stay DIRTY so it saves again
        if (saveState == SaveState.SAVING) saveState = SaveState.SAVED
    }

    fun saveCurrentDiary() {
        viewModelScope.launch {
            if (saveState == SaveState.DIRTY) {
                saveCurrent()
            }
        }
    }

    // Outlives the ViewModel so a final save still completes after Back
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCleared() {
        if (saveState == SaveState.DIRTY && isReady) {
            val diaryId = activeDiaryId
            val date = currentDate
            val text = diaryText.text
            saveScope.launch { repository.saveEntry(diaryId, date, text) }
        }
        super.onCleared()
    }

    /* ---------------- SEARCH ---------------- */

    var searchQuery by mutableStateOf("")
        private set

    var searchResults by mutableStateOf<List<DiaryEntity>>(emptyList())
        private set

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        searchQuery = query

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isBlank()) {
                searchResults = emptyList()
                return@launch
            }
            delay(250) // wait for typing to pause
            searchResults = repository.search(activeDiaryId, query)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchQuery = ""
        searchResults = emptyList()
    }

    /* ---------------- BACKUP / EXPORT ---------------- */

    suspend fun getTotalEntryCount(): Int = repository.countEntries(activeDiaryId)

    suspend fun exportAsJson(): String {
        val array = org.json.JSONArray()

        repository.getAllEntries(activeDiaryId).forEach {
            val obj = org.json.JSONObject()
            obj.put("date", it.date)
            obj.put("content", it.content)
            array.put(obj)
        }

        return array.toString(2)
    }

    suspend fun exportAsMarkdown(): String {
        val all = repository.getAllEntries(activeDiaryId)
        return buildString {
            all.forEach { diary ->
                append("## ${diary.date}\n\n")
                append(diary.content)
                append("\n\n---\n\n")
            }
        }
    }

    /* ---------------- IMPORT TEXT ---------------- */

    fun previewImportCount(raw: String): Int = DiaryTextParser.parse(raw).size

    fun previewImportDates(raw: String): List<LocalDate> =
        DiaryTextParser.parse(raw).map { it.date }.sortedDescending()

    suspend fun importFromTextSafe(raw: String) {
        repository.replaceEntries(activeDiaryId, DiaryTextParser.parse(raw), clearFirst = false)
        // Refresh the open page in case the import replaced this day
        loadDiaryForDate(currentDate)
    }

    /* ---------------- RESTORE ---------------- */

    sealed class BackupEvent {
        data class Success(val message: String) : BackupEvent()
        data class Error(val message: String) : BackupEvent()
    }

    private val _backupEvents = MutableSharedFlow<BackupEvent>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val backupEvents = _backupEvents.asSharedFlow()

    enum class RestoreMode { OVERWRITE, MERGE }

    suspend fun restoreFromJson(json: String, mode: RestoreMode = RestoreMode.MERGE) {
        // 1. Parse and validate EVERYTHING first; nothing is written yet
        val parsed = try {
            val items = org.json.JSONArray(json)
            List(items.length()) { i ->
                val obj = items.getJSONObject(i)
                ParsedDiary(
                    date = LocalDate.parse(obj.getString("date")), // throws on an invalid date
                    content = obj.getString("content")
                )
            }
        } catch (e: Exception) {
            _backupEvents.emit(BackupEvent.Error("Invalid backup file"))
            return
        }

        // 2. Write all-or-nothing
        repository.replaceEntries(
            activeDiaryId,
            parsed,
            clearFirst = mode == RestoreMode.OVERWRITE
        )

        loadDiaryForDate(currentDate)
        _backupEvents.emit(BackupEvent.Success("Restore complete (${parsed.size} entries)"))
    }

    /* ---------------- FORMAT ---------------- */

    fun applyFormat(format: TextFormat) {
        val text = diaryText.text
        val selection = diaryText.selection
        // min/max: a selection dragged right-to-left has start > end
        val start = selection.min
        val end = selection.max
        val selected = text.substring(start, end)
        val (prefix, suffix) = when (format) {
            TextFormat.Bold -> "**" to "**"
            TextFormat.Italic -> "_" to "_"
            TextFormat.Underline -> "__" to "__"
        }
        val newText = text.substring(0, start) + prefix + selected + suffix + text.substring(end)
        val newCursor = end + prefix.length + suffix.length
        diaryText = TextFieldValue(text = newText, selection = TextRange(newCursor))
        saveState = SaveState.DIRTY
        scheduleAutoSave()
    }

    /* ---------------- TEMPLATE INSERT ---------------- */

    fun insertTemplate(template: DiaryTemplate) {
        val insertion = "\n${template.emoji} ${template.label}:\n"
        val current = diaryText.text
        val cursorPos = diaryText.selection.max
        val newText = current.substring(0, cursorPos) + insertion + current.substring(cursorPos)
        val newCursor = cursorPos + insertion.length
        diaryText = TextFieldValue(text = newText, selection = TextRange(newCursor))
        saveState = SaveState.DIRTY
        scheduleAutoSave()
    }

    /* ---------------- PAGE PRELOAD ---------------- */

    data class DiaryPageState(
        val date: LocalDate,
        val text: String
    )

    suspend fun preloadPages(center: LocalDate): Map<Int, DiaryPageState> {
        val diaryId = activeDiaryFlow.filterNotNull().first()
        val prev = repository.getEntry(diaryId, center.minusDays(1))
        val next = repository.getEntry(diaryId, center.plusDays(1))
        return mapOf(
            -1 to DiaryPageState(date = center.minusDays(1), text = prev?.content.orEmpty()),
            0 to DiaryPageState(date = center, text = diaryText.text),
            1 to DiaryPageState(date = center.plusDays(1), text = next?.content.orEmpty())
        )
    }

    /* ---------------- REINITIALIZE ---------------- */

    fun reinitializeIfEmpty() {
        viewModelScope.launch {
            val diaryId = activeDiaryFlow.filterNotNull().first()
            if (repository.countEntries(diaryId) == 0) {
                saveState = SaveState.IDLE
                diaryText = TextFieldValue("")
                currentDate = LocalDate.now()
            }
        }
    }
}
