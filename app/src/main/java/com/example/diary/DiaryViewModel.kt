

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
        private set

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

            // Ensure at least one diary exists
            if (dao.getDiaryCount() == 0) {
                dao.insertDiary(
                    DiaryOwnerEntity(
                        title = "My Diary",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            val diaries = dao.getAllDiaries()

            if (diaries.isNotEmpty()) {
                activeDiaryId = diaries.first().diaryId
            }

            loadAllEntries()
            isReady = true

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

//    suspend fun restoreFromJson(json: String) {
//        val items = org.json.JSONArray(json)
//
//        dao.clearDiary(activeDiaryId)
//
//        for (i in 0 until items.length()) {
//            val obj = items.getJSONObject(i)
//
//            dao.insert(
//                DiaryEntity(
//                    diaryOwnerId = activeDiaryId,
//                    date = obj.getString("date"),
//                    content = obj.getString("content")
//                )
//            )
//        }
//
//        loadAllEntries()
//    }

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

//package com.example.diary
//
//import android.app.Application
//import androidx.lifecycle.AndroidViewModel
//import androidx.lifecycle.viewModelScope
//import com.example.diary.data.DiaryDatabase
//import com.example.diary.data.DiaryEntity
//import kotlinx.coroutines.launch
//import java.time.LocalDate
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.delay
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.text.input.TextFieldValue
//import kotlinx.coroutines.flow.asSharedFlow
//import androidx.compose.ui.text.TextRange
//import com.example.diary.data.DiaryOwnerEntity
//import kotlinx.coroutines.runBlocking
//import java.time.format.DateTimeFormatter
//import java.util.Locale
//
//
//
//
//class DiaryViewModel(application: Application) : AndroidViewModel(application) {
//
//    var activeDiaryId: Int = 0
//        private set
//
//
//    private val dao = DiaryDatabase
//        .getDatabase(application)
//        .diaryDao()
//
//    // ---- BACK-UP ----
//
//
//    sealed interface BackupEvent {
//        data class Success(val message: String) : BackupEvent
//        data class Error(val message: String) : BackupEvent
//    }
//
//    enum class RestoreMode {
//        MERGE,
//        OVERWRITE
//    }
//
//
//    fun previewImportCount(raw: String): Int {
//        return parseTextToEntries(raw).size
//    }
//
//
//    // ---- UI STATE ----
//    var diaries: List<DiaryEntity> = emptyList()
//        private set
//
//    var activeDiaryIndex: Int = 0
//        private set
//
//    var diaryText by mutableStateOf(TextFieldValue(""))
//        private set
//
//
//    enum class SaveState {
//        IDLE,
//        SAVED,
//        SAVING,
//        DIRTY
//    }
//
//    var saveState by mutableStateOf(SaveState.SAVED)
//        private set
//
//
//    init {
//        viewModelScope.launch {
//
//            // 1️⃣ Ensure at least one diary exists
//            if (dao.getDiaryCount() == 0) {
//                dao.insertDiary(
//                    DiaryOwnerEntity(
//                        title = "My Diary",
//                        createdAt = System.currentTimeMillis()
//                    )
//                )
//            }
//
//            // 2️⃣ Load diaries
//            val diariesList = dao.getAllDiaries()
//
//            if (diariesList.isNotEmpty()) {
//                activeDiaryId = diariesList.first().diaryId
//            }
//
//            // 3️⃣ Now safe to load entries
//            loadAll()
//        }
//    }
//
//
//    private fun loadAll() {
//        viewModelScope.launch {
//
//            diaries = dao.getAllEntries(activeDiaryId)
//
//        }
//    }
//
//    fun saveDiary(
//        date: LocalDate,
//        content: String
//    ) {
//        viewModelScope.launch {
//            dao.insert(
//                DiaryEntity(
//                    diaryOwnerId = activeDiaryId,
//                    date = date.toString(),
//                    content = content
//                )
//            )
//            loadAll()
//        }
//    }
//
//    fun loadDiaryForDate(date: LocalDate) {
//        currentDate = date
//
//        viewModelScope.launch {
//            val diary = dao.getEntryByDate(
//                activeDiaryId,
//                date.toString()
//            )
//            diaryText = TextFieldValue(diary?.content ?: "")
//        }
//    }
//
//
//    fun updateDiaryText(newValue: TextFieldValue) {
//        diaryText = newValue
//        saveState = SaveState.DIRTY
//        scheduleAutoSave()
//
//    }
//
//    private var autosaveJob: Job? = null
//
//    fun scheduleAutoSave() {
//        autosaveJob?.cancel()
//        autosaveJob = viewModelScope.launch {
//            delay(800)
//            saveCurrentDiaryInternal()
//        }
//    }
//
//    private suspend fun saveCurrentDiaryInternal() {
//        if (saveState != SaveState.DIRTY) return
//
//        saveState = SaveState.SAVING
//
//        dao.insert(
//            DiaryEntity(
//                diaryOwnerId = activeDiaryId,
//                date = currentDate.toString(),
//                content = diaryText.text
//            )
//        )
//
//
//        saveState = SaveState.SAVED
//    }
//
//
//    var isEditMode by mutableStateOf(true)
//        private set
//
//    fun updateEditModeForDate(date: LocalDate) {
//        isEditMode = date == LocalDate.now()
//    }
//
//    fun enableEditMode() {
//        isEditMode = true
//    }
//
//
//    var currentDate: LocalDate = LocalDate.now()
//
//    fun saveCurrentDiary() {
//        val text = diaryText.text
//        if (text.isBlank()) return
//
//        viewModelScope.launch {
//            dao.insert(
//                DiaryEntity(
//                    diaryOwnerId = activeDiaryId,
//                    date = currentDate.toString(),
//                    content = text
//                )
//            )
//        }
//    }
//
//
//    fun insertTemplate(template: DiaryTemplate) {
//        val current = diaryText
//        val text = current.text
//        val cursor = current.selection.start
//
//        val needsNewLine =
//            cursor > 0 && text.getOrNull(cursor - 1) != '\n'
//
//        val insertText = buildString {
//            if (needsNewLine) append("\n")
//            append(template.emoji)
//            append(" ")
//            append(template.label)
//            append("\n")
//        }
//
//        val newText =
//            text.substring(0, cursor) +
//                    insertText +
//                    text.substring(cursor)
//
//        val newCursor = cursor + insertText.length
//
//        diaryText = current.copy(
//            text = newText,
//            selection = TextRange(newCursor)
//        )
//    }
//
//    fun applyFormat(format: TextFormat) {
//        val current = diaryText
//        val text = current.text
//        val selection = current.selection
//
//        val (startTag, endTag) = when (format) {
//            TextFormat.Bold -> "**" to "**"
//            TextFormat.Underline -> "__" to "__"
//            text = newText,
//            selection = newSelection
//        )
//    }
//
//    var searchQuery by mutableStateOf("")
//        private set
//
//    var searchResults by mutableStateOf<List<DiaryEntity>>(emptyList())
//        private set
//
//    fun onSearchQueryChanged(query: String) {
//        searchQuery = query
//
//        viewModelScope.launch {
//            searchResults =
//                if (query.isBlank()) {
//                    emptyList()
//                } else {
//                    dao.searchEntries(
//                        activeDiaryId,
//                        "%$query%"
//                    )
//                }
//        }
//    }
//
//    fun clearSearch() {
//        searchQuery = ""
//        searchResults = emptyList()
//    }
//
//    fun forceSaveIfDirty() {
//        if (saveState == SaveState.DIRTY) {
//            viewModelScope.launch {
//                saveCurrentDiaryInternal()
//            }
//        }
//    }
//
//
//    suspend fun exportAsJson(): String {
//        val array = org.json.JSONArray()
//
//        dao.getAllEntries(activeDiaryId).forEach { diary ->
//            val obj = org.json.JSONObject().apply {
//                put("date", diary.date)
//                put("content", diary.content)
//            }
//            array.put(obj)
//        }
//
//        return array.toString(2) // pretty printed
//    }
//
//
//    suspend fun exportAsMarkdown(): String {
//        val all = dao.getAllEntries(activeDiaryId)
//        return buildString {
//            all.forEach { diary ->
//                append("## ${diary.date}\n\n")
//                append(diary.content)
//                append("\n\n---\n\n")
//            }
//        }
//    }
//
//
////    private val _backupEvents =
////        kotlinx.coroutines.flow.MutableSharedFlow<BackupEvent>()
//
//
//    private val _backupEvents =
//        kotlinx.coroutines.flow.MutableSharedFlow<BackupEvent>(
//            replay = 0,
//            extraBufferCapacity = 1
//        )
//    val backupEvents = _backupEvents.asSharedFlow()
//
//
//    suspend fun restoreFromJson(
//        json: String,
//        mode: RestoreMode
//    ) {
//        try {
//            val items = org.json.JSONArray(json)
//
//            if (mode == RestoreMode.OVERWRITE) {
//                dao.clearDiary(activeDiaryId)
//            }
//
//            for (i in 0 until items.length()) {
//                val obj = items.getJSONObject(i)
//
//                dao.insert(
//                    DiaryEntity(
//                        diaryOwnerId = activeDiaryId,
//                        date = obj.getString("date"),
//                        content = obj.getString("content")
//                    )
//                )
//
//
//            }
//
//            _backupEvents.emit(
//                BackupEvent.Success(
//                    "Restore complete (${items.length()} entries)"
//                )
//            )
//        } catch (e: Exception) {
//            _backupEvents.emit(
//                BackupEvent.Error("Invalid backup file")
//            )
//        }
//    }
//
//    suspend fun getTotalEntryCount(): Int {
//        return dao.getCount(activeDiaryId)
//    }
//
//
//    suspend fun importFromTextSafe(raw: String) {
//        val entries = parseTextToEntries(raw)
//
//        entries.forEach {
//
//            dao.insert(
//                DiaryEntity(
//                    diaryOwnerId = activeDiaryId,
//                    date = it.date.toString(),
//                    content = it.content
//                )
//            )
//
//
//        }
//
//        _backupEvents.emit(
//            BackupEvent.Success("Imported ${entries.size} entries")
//        )
//    }
//
//
//    fun reinitializeIfEmpty() {
//        viewModelScope.launch {
//            if (dao.getAllEntries(activeDiaryId).isEmpty())
//                saveState = SaveState.IDLE
//            diaryText = TextFieldValue("")
//            currentDate = LocalDate.now()
//        }
//    }
//}
//
//
//
//    private val supportedDateFormats = listOf(
//        // Numeric
//        DateTimeFormatter.ISO_LOCAL_DATE,                  // 2026-01-03
//        DateTimeFormatter.ofPattern("dd.MM.yyyy"),         // 03.01.2026
//        DateTimeFormatter.ofPattern("yyyy/MM/dd"),         // 2026/01/03
//
//        // Month names (case-insensitive)
//        DateTimeFormatter.ofPattern("dd MMM yyyy"),
//        DateTimeFormatter.ofPattern("dd MMMM yyyy")
//    )
//    private fun parseDateSafe(line: String): LocalDate? {
//        val cleaned = line
//            .replace("\uFEFF", "")                 // BOM
//            .replace(Regex("[\\u200B-\\u200D]"), "") // zero-width chars
//            .replace(Regex("\\s+"), " ")
//            .trim()
//
//        if (cleaned.isEmpty()) return null
//
//        /* ---------- 1️⃣ NUMERIC DATE FORMATS ---------- */
//
//        val numericFormats = listOf(
//            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
//            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
//            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
//            DateTimeFormatter.ofPattern("yyyy.MM.dd")
//        )
//
//        numericFormats.forEach { formatter ->
//            runCatching {
//                return LocalDate.parse(cleaned, formatter)
//            }
//        }
//
//        /* ---------- 2️⃣ TEXT MONTH FORMATS ---------- */
//
//        val regex = Regex("""^(\d{1,2})\s+([A-Za-z]{3,9})\s+(\d{4})$""")
//        val match = regex.matchEntire(cleaned) ?: return null
//
//        val day = match.groupValues[1].toInt()
//        val monthName = match.groupValues[2].lowercase(Locale.ENGLISH)
//        val year = match.groupValues[3].toInt()
//
//        val month = when (monthName.take(3)) {
//            "jan" -> 1
//            "feb" -> 2
//            "mar" -> 3
//            "apr" -> 4
//            "may" -> 5
//            "jun" -> 6
//            "jul" -> 7
//            "aug" -> 8
//            "sep" -> 9
//            "oct" -> 10
//            "nov" -> 11
//            "dec" -> 12
//            else -> return null
//        }
//
//        return runCatching {
//            LocalDate.of(year, month, day)
//        }.getOrNull()
//    }
//
//
//
//
//    data class ParsedDiary(
//        val date: LocalDate,
//        val content: String
//    )
//
//    private fun parseTextToEntries(raw: String): List<ParsedDiary> {
//        var currentDate: LocalDate? = null
//        val buffer = StringBuilder()
//        val result = mutableListOf<ParsedDiary>()
//
//        raw.lineSequence().forEach { line ->
//            val date = parseDateSafe(line)
//
//            if (date != null) {
//                if (currentDate != null && buffer.isNotBlank()) {
//                    result.add(
//                        ParsedDiary(
//                            date = currentDate!!,
//                            content = buffer.toString().trim()
//                        )
//                    )
//                }
//                currentDate = date
//                buffer.clear()
//            } else if (currentDate != null) {
//                buffer.appendLine(line)
//            }
//        }
//
//        if (currentDate != null && buffer.isNotBlank()) {
//            result.add(
//                ParsedDiary(
//                    date = currentDate!!,
//                    content = buffer.toString().trim()
//                )
//            )
//        }
//
//        return result
//    }
//
//    fun previewImportDates(raw: String): List<LocalDate> {
//        return parseTextToEntries(raw)
//            .map { it.date }
//            .sortedDescending()
//    }
//
//    data class DiaryPageState(
//        val date: LocalDate,
//        val text: String
//    )
//
//
//suspend fun preloadPages(center: LocalDate): Map<Int, DiaryPageState> {
//
//    val prev = dao.getEntryByDate(
//        activeDiaryId,
//        center.minusDays(1).toString()
//    )
//
//    val next = dao.getEntryByDate(
//        activeDiaryId,
//        center.plusDays(1).toString()
//    )
//
//    return mapOf(
//        -1 to DiaryPageState(
//            date = center.minusDays(1),
//            text = prev?.content.orEmpty()
//        ),
//        0 to DiaryPageState(
//            date = center,
//            text = diaryText.text
//        ),
//        1 to DiaryPageState(
//            date = center.plusDays(1),
//            text = next?.content.orEmpty()
//        )
//    )
//}
//
//}
//
//
//
//
//
//
//
//
//
//
//
//            TextFormat.Italic -> "_" to "_"
//
//        }
//
//        val start = selection.start
//        val end = selection.end
//
//        val newText: String
//        val newSelection: TextRange
//
//        if (start != end) {
//            // Text selected → wrap selection
//            newText =
//                text.substring(0, start) +
//                        startTag +
//                        text.substring(start, end) +
//                        endTag +
//                        text.substring(end)
//
//            newSelection = TextRange(
//                start + startTag.length,
//                end + startTag.length
//            )
//        } else {
//            // No selection → insert tags and place cursor inside
//            newText =
//                text.substring(0, start) +
//                        startTag +
//                        endTag +
//                        text.substring(start)
//
//            val cursorPos = start + startTag.length
//            newSelection = TextRange(cursorPos)
//        }
//
//        diaryText = current.copy(
