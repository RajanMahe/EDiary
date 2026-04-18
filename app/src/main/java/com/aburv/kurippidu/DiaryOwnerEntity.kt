package com.aburv.kurippidu.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diaries")
data class DiaryOwnerEntity(

    @PrimaryKey(autoGenerate = true)
    val diaryId: Int = 0,

    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)
