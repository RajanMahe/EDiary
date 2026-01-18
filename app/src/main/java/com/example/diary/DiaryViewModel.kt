package com.example.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.diary.data.DiaryDatabase
import com.example.diary.data.DiaryEntity
import kotlinx.coroutines.launch
import java.time.LocalDate

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue





class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = DiaryDatabase
        .getDatabase(application)
        .diaryDao()

    // ---- UI STATE ----
    var diaries: List<DiaryEntity> = emptyList()
        private set

    var activeDiaryIndex: Int = 0
        private set

    var diaryText by mutableStateOf(TextFieldValue(""))
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



}
