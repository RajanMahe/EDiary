package com.aburv.kurippidu.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todo",
    indices = [Index(value = ["parentId"])]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val diaryId: Int,
    val title: String,
    val date: String,
    val type: String, // see TodoType
    val startDate: String? = null,
    val endDate: String? = null,
    val time: Long? = null,
    val isDone: Boolean = false,
    // For DAILY_OVERRIDE rows: id of the RECURRING task this day belongs to
    val parentId: Int? = null
)

object TodoType {
    const val DAILY = "DAILY"
    const val RECURRING = "RECURRING"
    const val DAILY_OVERRIDE = "DAILY_OVERRIDE"
}
