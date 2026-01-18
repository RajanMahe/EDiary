package com.example.diary

import java.time.LocalDate
import java.util.UUID

data class DiaryEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String = ""
)

enum class InsertType {
    Workout, Food, Spent
}

fun InsertType.label(): String =
    when (this) {
        InsertType.Workout -> "🏋️"
        InsertType.Food -> "🍔"
        InsertType.Spent -> "💸"
    }
