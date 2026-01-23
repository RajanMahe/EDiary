package com.example.diary

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun saveToDownloads(
    context: Context,
    content: String,
    extension: String
): File {
    val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
        .format(Date())

    val dir = File(
        Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ),
        "EDiary"
    )

    if (!dir.exists()) dir.mkdirs()

    val file = File(dir, "diary_backup_$timeStamp.$extension")
    file.writeText(content)

    return file
}
