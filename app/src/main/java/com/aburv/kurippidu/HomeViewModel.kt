package com.aburv.kurippidu

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import com.aburv.kurippidu.data.DiaryDatabase
import com.aburv.kurippidu.data.DiaryOwnerEntity
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = DiaryDatabase
        .getDatabase(application)
        .diaryDao()

    var diaries by mutableStateOf<List<DiaryOwnerEntity>>(emptyList())
        private set

    var entryCounts by mutableStateOf<Map<Int, Int>>(emptyMap())
        private set

    var isLoading by mutableStateOf(true)
        private set

    init {
        loadDiaries()
    }

    fun loadDiaries() {
        viewModelScope.launch {
            val loaded = dao.getAllDiaries()
            diaries = loaded
            entryCounts = loaded.associate { diary ->
                diary.diaryId to dao.getEntryCountForDiary(diary.diaryId)
            }
            isLoading = false
        }
    }

    fun createDiary(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            dao.insertDiary(
                DiaryOwnerEntity(
                    title = title.trim(),
                    createdAt = System.currentTimeMillis()
                )
            )
            loadDiaries()
        }
    }

    fun deleteDiary(diary: DiaryOwnerEntity) {
        viewModelScope.launch {
            // Room CASCADE will auto-delete all entries for this diary
            dao.deleteDiary(diary)
            loadDiaries()
        }
    }

    fun renameDiary(diary: DiaryOwnerEntity, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            dao.updateDiary(diary.copy(title = newTitle.trim()))
            loadDiaries()
        }
    }
}