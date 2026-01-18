package com.example.diary.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "diary",
    indices = [Index(value = ["date"], unique = true)])
data class DiaryEntity(

    @PrimaryKey(autoGenerate = true)
    val id:Int =0,
    val date: String,   // yyyy-MM-dd
    val content: String
)
