package com.aburv.kurippidu

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val diaryId: Int,
    val title: String,
    val date: String,
    val type: String, // DAILY / RECURRING
    val startDate: String? = null,
    val endDate: String? = null,
    val time: Long? = null,
    val isDone: Boolean = false
)