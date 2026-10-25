package com.aburv.kurippidu

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aburv.kurippidu.data.DiaryOwnerEntity
import com.aburv.kurippidu.data.DiaryRepository
import com.aburv.kurippidu.data.DiaryType
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DiaryRepository.get(application)

    var diaries by mutableStateOf<List<DiaryOwnerEntity>>(emptyList())
        private set

    var entryCounts by mutableStateOf<Map<Int, Int>>(emptyMap())
        private set

    var isLoading by mutableStateOf(true)
        private set

    /** Days in a row with writing, per diary id. */
    var streaks by mutableStateOf<Map<Int, Int>>(emptyMap())
        private set

    init {
        // Room re-emits on every change, so the list and counts stay current
        // after creating a diary or writing entries inside one.
        viewModelScope.launch {
            repository.observeDiariesWithCounts().collect { rows ->
                diaries = rows.map { it.diary }
                entryCounts = rows.associate { it.diary.diaryId to it.entryCount }
                isLoading = false
            }
        }
        viewModelScope.launch {
            repository.observeWritingStreaks().collect { streaks = it }
        }
    }

    /** A blank title gets the type's name ("Gym log"). */
    fun createDiary(title: String, type: DiaryType = DiaryType.PLAIN) {
        viewModelScope.launch { repository.createDiary(title, type) }
    }

    fun setDiaryType(diary: DiaryOwnerEntity, type: DiaryType) {
        viewModelScope.launch { repository.setDiaryType(diary, type) }
    }

    fun deleteDiary(diary: DiaryOwnerEntity) {
        viewModelScope.launch { repository.deleteDiary(diary) }
    }

    fun renameDiary(diary: DiaryOwnerEntity, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch { repository.renameDiary(diary, newTitle) }
    }
}
