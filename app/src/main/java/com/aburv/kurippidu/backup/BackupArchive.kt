package com.aburv.kurippidu.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup file on disk. Version 3 is a zip:
 *   backup.json            – everything except photos
 *   attachments/<name>.jpg – the photos
 * Older backups are a plain JSON file.
 */
object BackupArchive {

    const val JSON_NAME = "backup.json"
    private const val PHOTO_DIR = "attachments/"
    private val SAFE_NAME = Regex("[A-Za-z0-9._-]+")

    fun write(out: OutputStream, json: String, photos: List<File>) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_NAME))
            zip.write(json.toByteArray())
            zip.closeEntry()
            photos.filter { it.isFile }.forEach { photo ->
                zip.putNextEntry(ZipEntry(PHOTO_DIR + photo.name))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** A zip starts with "PK". */
    fun isZip(bytes: ByteArray): Boolean = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /**
     * Reads a backup: returns its JSON text, and unpacks any photos into [photoDir].
     * Photo names are checked so a crafted zip can't write outside that folder.
     */
    fun read(bytes: ByteArray, photoDir: File): String {
        if (!isZip(bytes)) return bytes.toString(Charsets.UTF_8)

        var json: String? = null
        ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                when {
                    entry.isDirectory -> Unit
                    entry.name == JSON_NAME -> json = zip.readBytes().toString(Charsets.UTF_8)
                    entry.name.startsWith(PHOTO_DIR) -> {
                        val name = entry.name.removePrefix(PHOTO_DIR)
                        if (name.matches(SAFE_NAME) && name != "." && name != "..") {
                            photoDir.mkdirs()
                            val target = File(photoDir, name)
                            if (!target.exists()) target.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
            }
        }
        return json ?: throw com.aburv.kurippidu.data.backup.BackupFormatException("Backup file has no backup.json")
    }

    fun readAll(input: InputStream): ByteArray = ByteArrayOutputStream().also { input.copyTo(it) }.toByteArray()
}
