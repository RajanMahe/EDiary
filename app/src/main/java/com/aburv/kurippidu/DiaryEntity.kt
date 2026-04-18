package com.aburv.kurippidu.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

import androidx.room.*

@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = DiaryOwnerEntity::class,
            parentColumns = ["diaryId"],
            childColumns = ["diaryOwnerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["diaryOwnerId"]),
        Index(value = ["diaryOwnerId", "date"], unique = true)
    ]
)
data class DiaryEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val diaryOwnerId: Int,   // 🔥 NEW

    val date: String,
    val content: String
)
