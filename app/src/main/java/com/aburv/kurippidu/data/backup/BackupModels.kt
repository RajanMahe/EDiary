package com.aburv.kurippidu.data.backup

import kotlinx.serialization.Serializable

/**
 * Full backup file: every diary with its entries and tasks, plus settings.
 *
 * Version history
 * - 1: a plain JSON array of {date, content} for one diary (still readable)
 * - 2: this object, as a .json file
 * - 3: same object inside a .zip together with the photos (attachments/…)
 *
 * New fields must have defaults so older files keep decoding.
 */
@Serializable
data class FullBackup(
    val app: String = APP_ID,
    val version: Int = CURRENT_VERSION,
    val exportedAt: String,
    val settings: BackupSettings = BackupSettings(),
    val diaries: List<BackupDiary> = emptyList()
) {
    companion object {
        const val APP_ID = "kurippidu"
        const val CURRENT_VERSION = 3
    }
}

@Serializable
data class BackupSettings(
    val themeMode: String? = null,
    val lockMode: String? = null,
    val fontSize: String? = null
)

@Serializable
data class BackupDiary(
    val title: String,
    val createdAt: Long = 0,
    val entries: List<BackupEntry> = emptyList(),
    val todos: List<BackupTodo> = emptyList()
)

@Serializable
data class BackupEntry(
    val date: String,
    val content: String,
    val mood: String? = null,
    val updatedAt: Long? = null,
    val attachments: List<String> = emptyList()
)

/**
 * [key] identifies a task inside this file only; per-day checks of a
 * recurring task point at it with [parentKey].
 */
@Serializable
data class BackupTodo(
    val key: Int,
    val title: String,
    val date: String,
    val type: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val time: Long? = null,
    val isDone: Boolean = false,
    val parentKey: Int? = null,
    val repeatDays: Int? = null
)

/** What a restore added or changed, for the confirmation message. */
data class RestoreSummary(
    val diaries: Int,
    val entries: Int,
    val todos: Int
)
