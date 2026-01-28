package com.example.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.diary.data.DiaryDatabase
import com.example.diary.data.DiaryEntity
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.asSharedFlow
import androidx.compose.ui.text.TextRange
import java.time.format.DateTimeFormatter
import java.util.Locale




class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = DiaryDatabase
        .getDatabase(application)
        .diaryDao()

    // ---- BACK-UP ----


    sealed interface BackupEvent {
        data class Success(val message: String) : BackupEvent
        data class Error(val message: String) : BackupEvent
    }
    enum class RestoreMode {
        MERGE,
        OVERWRITE
    }



    fun previewImportCount(raw: String): Int {
        return parseTextToEntries(raw).size
    }




    // ---- UI STATE ----
    var diaries: List<DiaryEntity> = emptyList()
        private set

    var activeDiaryIndex: Int = 0
        private set

    var diaryText by mutableStateOf(TextFieldValue(""))
        private set


    enum class SaveState {
        IDLE,
        SAVED,
        SAVING,
        DIRTY
    }
    var saveState by mutableStateOf(SaveState.SAVED)
        private set




    init {
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            diaries = dao.getAll()
            activeDiaryIndex = diaries.indices.firstOrNull() ?: 0
        }
    }

    fun saveDiary(
        date: LocalDate,
        content: String
    ) {
        viewModelScope.launch {
            dao.insert(
                DiaryEntity(
                    date = date.toString(),
                    content = content
                )
            )
            loadAll()
        }
    }
    fun loadDiaryForDate(date: LocalDate) {
        currentDate = date

        viewModelScope.launch {
            val diary = dao.getDiaryByDate(date.toString())
            diaryText = TextFieldValue(diary?.content ?: "")
        }
    }



    fun updateDiaryText(newValue: TextFieldValue) {
        diaryText = newValue
        saveState = SaveState.DIRTY
        scheduleAutoSave()

    }

    private var autosaveJob: Job? = null

    fun scheduleAutoSave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(800)
            saveCurrentDiaryInternal()
        }
    }

    private suspend fun saveCurrentDiaryInternal() {
        if (saveState != SaveState.DIRTY) return

        saveState = SaveState.SAVING

        dao.insert(
            DiaryEntity(
                date = currentDate.toString(),
                content = diaryText.text
            )
        )

        saveState = SaveState.SAVED
    }



    var isEditMode by mutableStateOf(true)
        private set

    fun updateEditModeForDate(date: LocalDate) {
        isEditMode = date == LocalDate.now()
    }

    fun enableEditMode() {
        isEditMode = true
    }




    var currentDate: LocalDate = LocalDate.now()

    fun saveCurrentDiary() {
        val text = diaryText.text
        if (text.isBlank()) return

        viewModelScope.launch {
            dao.insert(
                DiaryEntity(
                    date = currentDate.toString(),
                    content = text
                )
            )
        }
    }

    fun insertTemplate(template: DiaryTemplate) {
        val current = diaryText
        val text = current.text
        val cursor = current.selection.start

        val needsNewLine =
            cursor > 0 && text.getOrNull(cursor - 1) != '\n'

        val insertText = buildString {
            if (needsNewLine) append("\n")
            append(template.emoji)
            append(" ")
            append(template.label)
            append("\n")
        }

        val newText =
            text.substring(0, cursor) +
                    insertText +
                    text.substring(cursor)

        val newCursor = cursor + insertText.length

        diaryText = current.copy(
            text = newText,
            selection = TextRange(newCursor)
        )
    }
    fun applyFormat(format: TextFormat) {
        val current = diaryText
        val text = current.text
        val selection = current.selection

        val (startTag, endTag) = when (format) {
            TextFormat.Bold -> "**" to "**"
            TextFormat.Underline -> "__" to "__"
            TextFormat.Italic -> "_" to "_"

        }

        val start = selection.start
        val end = selection.end

        val newText: String
        val newSelection: TextRange

        if (start != end) {
            // Text selected → wrap selection
            newText =
                text.substring(0, start) +
                        startTag +
                        text.substring(start, end) +
                        endTag +
                        text.substring(end)

            newSelection = TextRange(
                start + startTag.length,
                end + startTag.length
            )
        } else {
            // No selection → insert tags and place cursor inside
            newText =
                text.substring(0, start) +
                        startTag +
                        endTag +
                        text.substring(start)

            val cursorPos = start + startTag.length
            newSelection = TextRange(cursorPos)
        }

        diaryText = current.copy(
            text = newText,
            selection = newSelection
        )
    }

    var searchQuery by mutableStateOf("")
        private set

    var searchResults by mutableStateOf<List<DiaryEntity>>(emptyList())
        private set

    fun onSearchQueryChanged(query: String) {
        searchQuery = query

        viewModelScope.launch {
            searchResults =
                if (query.isBlank()) {
                    emptyList()
                } else {
                    dao.searchDiary("%$query%")
                }
        }
    }
    fun clearSearch() {
        searchQuery = ""
        searchResults = emptyList()
    }

    fun forceSaveIfDirty() {
        if (saveState == SaveState.DIRTY) {
            viewModelScope.launch {
                saveCurrentDiaryInternal()
            }
        }
    }


    suspend fun exportAsJson(): String {
        val array = org.json.JSONArray()

        dao.getAll().forEach { diary ->
            val obj = org.json.JSONObject().apply {
                put("date", diary.date)
                put("content", diary.content)
            }
            array.put(obj)
        }

        return array.toString(2) // pretty printed
    }


    suspend fun exportAsMarkdown(): String {
        val all = dao.getAll()
        return buildString {
            all.forEach { diary ->
                append("## ${diary.date}\n\n")
                append(diary.content)
                append("\n\n---\n\n")
            }
        }
    }


//    private val _backupEvents =
//        kotlinx.coroutines.flow.MutableSharedFlow<BackupEvent>()


    private val _backupEvents =
        kotlinx.coroutines.flow.MutableSharedFlow<BackupEvent>(
            replay = 0,
            extraBufferCapacity = 1
        )
        val backupEvents = _backupEvents.asSharedFlow()


    suspend fun restoreFromJson(
        json: String,
        mode: RestoreMode
    ) {
        try {
            val items = org.json.JSONArray(json)

            if (mode == RestoreMode.OVERWRITE) {
                dao.clearAll()   // ⚠️ requires DAO method (see step 2a)
            }

            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)

                dao.insert(
                    DiaryEntity(
                        date = obj.getString("date"),
                        content = obj.getString("content")
                    )
                )
            }

            _backupEvents.emit(
                BackupEvent.Success(
                    "Restore complete (${items.length()} entries)"
                )
            )
        } catch (e: Exception) {
            _backupEvents.emit(
                BackupEvent.Error("Invalid backup file")
            )
        }
    }



    suspend fun getTotalEntryCount(): Int {
        return dao.getCount()
    }



    suspend fun importFromTextSafe(raw: String) {
        val entries = parseTextToEntries(raw)

        entries.forEach {
            dao.insert(
                DiaryEntity(
                    date = it.date.toString(),
                    content = it.content
                )
            )
        }

        _backupEvents.emit(
            BackupEvent.Success("Imported ${entries.size} entries")
        )
    }




    fun reinitializeIfEmpty() {
        viewModelScope.launch {
            if (dao.getAll().isEmpty()) {
                saveState = SaveState.IDLE
                diaryText = TextFieldValue("")
                currentDate = LocalDate.now()
            }
        }
    }


    private val supportedDateFormats = listOf(
        // Numeric
        DateTimeFormatter.ISO_LOCAL_DATE,                  // 2026-01-03
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),         // 03.01.2026
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),         // 2026/01/03

        // Month names (case-insensitive)
        DateTimeFormatter.ofPattern("dd MMM yyyy"),
        DateTimeFormatter.ofPattern("dd MMMM yyyy")
    )
    private fun parseDateSafe(line: String): LocalDate? {
        val cleaned = line
            .replace("\uFEFF", "")                 // BOM
            .replace(Regex("[\\u200B-\\u200D]"), "") // zero-width chars
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isEmpty()) return null

        /* ---------- 1️⃣ NUMERIC DATE FORMATS ---------- */

        val numericFormats = listOf(
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd")
        )

        numericFormats.forEach { formatter ->
            runCatching {
                return LocalDate.parse(cleaned, formatter)
            }
        }

        /* ---------- 2️⃣ TEXT MONTH FORMATS ---------- */

        val regex = Regex("""^(\d{1,2})\s+([A-Za-z]{3,9})\s+(\d{4})$""")
        val match = regex.matchEntire(cleaned) ?: return null

        val day = match.groupValues[1].toInt()
        val monthName = match.groupValues[2].lowercase(Locale.ENGLISH)
        val year = match.groupValues[3].toInt()

        val month = when (monthName.take(3)) {
            "jan" -> 1
            "feb" -> 2
            "mar" -> 3
            "apr" -> 4
            "may" -> 5
            "jun" -> 6
            "jul" -> 7
            "aug" -> 8
            "sep" -> 9
            "oct" -> 10
            "nov" -> 11
            "dec" -> 12
            else -> return null
        }

        return runCatching {
            LocalDate.of(year, month, day)
        }.getOrNull()
    }




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
                            date = currentDate!!,
                            content = buffer.toString().trim()
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
                    date = currentDate!!,
                    content = buffer.toString().trim()
                )
            )
        }

        return result
    }

    fun previewImportDates(raw: String): List<LocalDate> {
        return parseTextToEntries(raw)
            .map { it.date }
            .sortedDescending()
    }







}


