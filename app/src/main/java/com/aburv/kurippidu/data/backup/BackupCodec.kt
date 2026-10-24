package com.aburv.kurippidu.data.backup

import com.aburv.kurippidu.data.ParsedDiary
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A backup file as read from disk. */
sealed interface ParsedBackup {
    /** Version 2+: all diaries and settings. */
    data class Full(val backup: FullBackup) : ParsedBackup

    /** Version 1: entries for a single diary (restored into the open diary). */
    data class SingleDiary(val entries: List<ParsedDiary>) : ParsedBackup
}

object BackupCodec {

    private val json = Json {
        ignoreUnknownKeys = true // files from newer minor versions still open
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(backup: FullBackup): String = json.encodeToString(FullBackup.serializer(), backup)

    /** Reads any supported backup version. Everything is validated before returning. */
    fun decode(text: String): ParsedBackup {
        val root = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw BackupFormatException("Not a backup file", e)
        }

        return when (root) {
            is JsonArray -> ParsedBackup.SingleDiary(decodeVersion1(root))
            is JsonObject -> ParsedBackup.Full(decodeFull(root))
            else -> throw BackupFormatException("Not a backup file")
        }
    }

    private fun decodeVersion1(array: JsonArray): List<ParsedDiary> = array.map { element ->
        try {
            val obj = element.jsonObject
            ParsedDiary(
                date = LocalDate.parse(obj.getValue("date").jsonPrimitive.content),
                content = obj.getValue("content").jsonPrimitive.content
            )
        } catch (e: Exception) {
            throw BackupFormatException("Invalid entry in backup", e)
        }
    }

    private fun decodeFull(obj: JsonObject): FullBackup {
        val version = obj["version"]?.jsonPrimitive?.intOrNull
            ?: throw BackupFormatException("Backup has no version")
        if (version > FullBackup.CURRENT_VERSION) {
            throw BackupFormatException("This backup was made by a newer version of the app. Please update the app first.")
        }
        val app = obj["app"]?.jsonPrimitive?.contentOrNull
        if (app != null && app != FullBackup.APP_ID) {
            throw BackupFormatException("This file is not a Kurippidu backup")
        }

        val backup = try {
            json.decodeFromJsonElement(FullBackup.serializer(), obj)
        } catch (e: Exception) {
            throw BackupFormatException("Backup file is damaged", e)
        }
        validate(backup)
        return backup
    }

    private fun validate(backup: FullBackup) {
        backup.diaries.forEach { diary ->
            if (diary.title.isBlank()) throw BackupFormatException("A diary in the backup has no name")
            diary.entries.forEach { entry -> checkDate(entry.date) }

            val keys = diary.todos.map { it.key }.toSet()
            diary.todos.forEach { todo ->
                checkDate(todo.date)
                todo.startDate?.let(::checkDate)
                todo.endDate?.let(::checkDate)
                if (todo.parentKey != null && todo.parentKey !in keys) {
                    throw BackupFormatException("Backup has a task check without its task")
                }
            }
        }
    }

    private fun checkDate(value: String) {
        try {
            LocalDate.parse(value)
        } catch (e: Exception) {
            throw BackupFormatException("Invalid date in backup: $value", e)
        }
    }

}
