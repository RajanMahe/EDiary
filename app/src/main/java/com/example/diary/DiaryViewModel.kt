

package com.example.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.diary.data.*
import kotlinx.coroutines.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = DiaryDatabase
        .getDatabase(application)
        .diaryDao()

    /* ---------------- ACTIVE DIARY ---------------- */

    var activeDiaryId by mutableStateOf(0)
        internal set

    var isReady by mutableStateOf(false)
        private set

    /* ---------------- UI STATE ---------------- */

    var entries by mutableStateOf<List<DiaryEntity>>(emptyList())
        private set

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

    /* ---------------- INIT ---------------- */


    init {
        viewModelScope.launch {
            // Only ensure at least one diary exists in DB.
            // Do NOT set activeDiaryId here — wait for setActiveDiary() from navigation.
            if (dao.getDiaryCount() == 0) {
                dao.insertDiary(
                    DiaryOwnerEntity(
                        title = "My Diary",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
            // isReady stays false until setActiveDiary() is called
        }
    }

    /* ---------------- LOAD ---------------- */

    private suspend fun loadAllEntries() {
        entries = dao.getAllEntries(activeDiaryId)
    }

    fun loadDiaryForDate(date: LocalDate) {
        currentDate = date

        viewModelScope.launch {
            while (!isReady) delay(50)

            val entry = dao.getEntryByDate(activeDiaryId, date.toString())
            diaryText = TextFieldValue(entry?.content ?: "")
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
        if (saveState != SaveState.DIRTY) return

        saveState = SaveState.SAVING

        dao.insert(
            DiaryEntity(
                diaryOwnerId = activeDiaryId,
                date = currentDate.toString(),
                content = diaryText.text
            )
        )

        saveState = SaveState.SAVED
        loadAllEntries()
    }

    fun saveCurrentDiary() {
        viewModelScope.launch {
            if (saveState == SaveState.DIRTY) {
                saveCurrent()
            }
        }
    }

    /* ---------------- SEARCH ---------------- */

    var searchQuery by mutableStateOf("")
        private set

    var searchResults by mutableStateOf<List<DiaryEntity>>(emptyList())
        private set

    fun onSearchQueryChanged(query: String) {
        searchQuery = query

        viewModelScope.launch {
            searchResults =
                if (query.isBlank()) emptyList()
                else dao.searchEntries(activeDiaryId, "%$query%")
        }
    }

    /* ---------------- BACKUP / EXPORT ---------------- */

    suspend fun getTotalEntryCount(): Int {
        return dao.getCount(activeDiaryId)
    }

    suspend fun exportAsJson(): String {
        val array = org.json.JSONArray()

        dao.getAllEntries(activeDiaryId).forEach {
            val obj = org.json.JSONObject()
            obj.put("date", it.date)
            obj.put("content", it.content)
            array.put(obj)
        }

        return array.toString(2)
    }


    /* ---------------- IMPORT TEXT ---------------- */

    fun previewImportCount(raw: String): Int {
        return parseTextToEntries(raw).size
    }

    fun previewImportDates(raw: String): List<LocalDate> {
        return parseTextToEntries(raw)
            .map { it.date }
            .sortedDescending()
    }

    suspend fun importFromTextSafe(raw: String) {
        val entries = parseTextToEntries(raw)

        entries.forEach {
            dao.insert(
                DiaryEntity(
                    diaryOwnerId = activeDiaryId,
                    date = it.date.toString(),
                    content = it.content
                )
            )
        }

        loadAllEntries()
    }

    /* ---------------- TEXT PARSER ---------------- */

    data class ParsedDiary(
        val date: LocalDate,
        val content: String
    )

    private fun parseTextToEntries(raw: String): List<ParsedDiary> {

        var currentDate: LocalDate? = null
        val buffer = StringBuilder()
        val result = mutableListOf<ParsedDiary>()

        raw.lineSequence().forEach { line ->
            val date = parseDateSafe(line)

            if (date != null) {
                if (currentDate != null && buffer.isNotBlank()) {
                    result.add(
                        ParsedDiary(
                            currentDate!!,
                            buffer.toString().trim()
                        )
                    )
                }
                currentDate = date
                buffer.clear()
            } else if (currentDate != null) {
                buffer.appendLine(line)
            }
        }

        if (currentDate != null && buffer.isNotBlank()) {
            result.add(
                ParsedDiary(
                    currentDate!!,
                    buffer.toString().trim()
                )
            )
        }

        return result
    }

    private fun parseDateSafe(line: String): LocalDate? {
        val cleaned = line.trim()

        val formats = listOf(
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy")
        )

        formats.forEach {
            runCatching {
                return LocalDate.parse(cleaned, it)
            }
        }

        return null
    }

    /* ---------------- FORMAT ---------------- */

    fun applyFormat(format: TextFormat) {
        val text = diaryText.text
        val selection = diaryText.selection
        val selected = text.substring(selection.start, selection.end)
        val (prefix, suffix) = when (format) {
            TextFormat.Bold -> "**" to "**"
            TextFormat.Italic -> "_" to "_"
            TextFormat.Underline -> "__" to "__"
        }
        val newText = text.substring(0, selection.start) +
                prefix + selected + suffix +
                text.substring(selection.end)
        val newCursor = selection.end + prefix.length + suffix.length
        diaryText = TextFieldValue(text = newText, selection = TextRange(newCursor))
        saveState = SaveState.DIRTY
        scheduleAutoSave()
    }

    /* ---------------- TEMPLATE INSERT ---------------- */

    fun insertTemplate(template: DiaryTemplate) {
        val insertion = "\n${template.emoji} ${template.label}:\n"
        val current = diaryText.text
        val cursorPos = diaryText.selection.end
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
        while (!isReady) delay(50)
        val prev = dao.getEntryByDate(activeDiaryId, center.minusDays(1).toString())
        val next = dao.getEntryByDate(activeDiaryId, center.plusDays(1).toString())
        return mapOf(
            -1 to DiaryPageState(date = center.minusDays(1), text = prev?.content.orEmpty()),
            0  to DiaryPageState(date = center, text = diaryText.text),
            1  to DiaryPageState(date = center.plusDays(1), text = next?.content.orEmpty())
        )
    }

    /* ---------------- SEARCH ---------------- */

    fun clearSearch() {
        searchQuery = ""
        searchResults = emptyList()
    }

    /* ---------------- BACKUP EVENTS ---------------- */

    sealed class BackupEvent {
        data class Success(val message: String) : BackupEvent()
        data class Error(val message: String) : BackupEvent()
    }

    private val _backupEvents = MutableSharedFlow<BackupEvent>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val backupEvents = _backupEvents.asSharedFlow()

    /* ---------------- EXPORT ---------------- */

    suspend fun exportAsMarkdown(): String {
        val all = dao.getAllEntries(activeDiaryId)
        return buildString {
            all.forEach { diary ->
                append("## ${diary.date}\n\n")
                append(diary.content)
                append("\n\n---\n\n")
            }
        }
    }

    /* ---------------- RESTORE ---------------- */

    enum class RestoreMode { OVERWRITE, MERGE }

    suspend fun restoreFromJson(json: String, mode: RestoreMode = RestoreMode.MERGE) {
        try {
            val items = org.json.JSONArray(json)
            if (mode == RestoreMode.OVERWRITE) {
                dao.clearDiary(activeDiaryId)
            }
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                dao.insert(
                    DiaryEntity(
                        diaryOwnerId = activeDiaryId,
                        date = obj.getString("date"),
                        content = obj.getString("content")
                    )
                )
            }
            loadAllEntries()
            _backupEvents.emit(BackupEvent.Success("Restore complete (${items.length()} entries)"))
        } catch (e: Exception) {
            _backupEvents.emit(BackupEvent.Error("Invalid backup file"))
        }
    }

    /* ---------------- REINITIALIZE ---------------- */

    fun setActiveDiary(id: Int) {
        viewModelScope.launch {
            // Guard: if already set to same diary, don't reload
            if (activeDiaryId == id && isReady) return@launch
            activeDiaryId = id
            isReady = true
            loadAllEntries()
            loadDiaryForDate(currentDate)
        }
    }


    fun reinitializeIfEmpty() {
        viewModelScope.launch {
            if (dao.getAllEntries(activeDiaryId).isEmpty()) {
                saveState = SaveState.IDLE
                diaryText = TextFieldValue("")
                currentDate = LocalDate.now()
            }
        }
    }


}