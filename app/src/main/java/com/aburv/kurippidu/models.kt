package com.aburv.kurippidu

import java.time.LocalDate
import java.util.UUID

data class DiaryEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String = ""
)

enum class InsertType {
    Workout, Food, Spent
}

enum class TextFormat {
    Bold,
    Italic,
    Underline
}


fun InsertType.label(): String =
    when (this) {
        InsertType.Workout -> "🏋️"
        InsertType.Food -> "🍔"
        InsertType.Spent -> "💸"
    }
