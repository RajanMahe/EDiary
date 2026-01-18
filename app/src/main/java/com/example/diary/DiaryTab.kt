// DiaryTab.kt
package com.example.diary

import androidx.compose.ui.text.input.TextFieldValue

data class DiaryTab(
    val id: Int = 0,
    val title: String,
    val content: TextFieldValue
)
