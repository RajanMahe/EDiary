package com.example.diary

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore


fun saveToDownloads(
    context: Context,
    content: String,
    extension: String
): File {
    val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
    val fileName = "diary_backup_$timeStamp.$extension"
    val mimeType = when (extension) {
        "json" -> "application/json"
        "md"   -> "text/markdown"
        else   -> "text/plain"
    }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/EDiary")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert failed")
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "EDiary/$fileName")
    } else {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "EDiary")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content)
        file
    }
}
