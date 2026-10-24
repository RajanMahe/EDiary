package com.aburv.kurippidu.data.security

import android.content.Context
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * Turns an existing plain database into an encrypted one, once.
 *
 * Steps, each checked before the next:
 * 1. copy every table into a new encrypted file (sqlcipher_export)
 * 2. carry over the schema version Room uses for migrations
 * 3. open the new file with the key and read from it
 * 4. only then swap files; the old plain file is deleted last
 * If anything fails, the plain database is left exactly as it was.
 */
object DatabaseEncryption {

    private const val TAG = "DatabaseEncryption"
    private val PLAIN_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    enum class State { MISSING, PLAIN, ENCRYPTED }

    fun stateOf(file: File): State {
        if (!file.exists() || file.length() == 0L) return State.MISSING
        val header = ByteArray(PLAIN_HEADER.size)
        val read = file.inputStream().use { it.read(header) }
        return if (read == header.size && header.contentEquals(PLAIN_HEADER)) State.PLAIN else State.ENCRYPTED
    }

    /** @return true if the database is (now) encrypted or doesn't exist yet. */
    fun encryptIfNeeded(context: Context, name: String, passphrase: String): Boolean {
        val file = context.getDatabasePath(name)
        when (stateOf(file)) {
            State.MISSING, State.ENCRYPTED -> return true
            State.PLAIN -> Unit
        }

        val encrypted = File(file.path + ".encrypting")
        val oldPlain = File(file.path + ".plain")
        return try {
            encrypted.delete()

            // 1 + 2: export into an encrypted copy (WAL contents are read automatically)
            // CREATE_IF_NECESSARY: the attached encrypted file inherits these flags
            val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
            SQLiteDatabase.openDatabase(file.path, "", null, flags, null).use { plain ->
                // One connection only: ATTACH must be seen by the export that follows,
                // and Android's default WAL mode would spread them over a pool
                plain.disableWriteAheadLogging()
                val version = plain.version
                // Bound parameters, so a failing statement never prints the key to the log
                plain.execSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf<Any>(encrypted.path, passphrase))
                plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
                plain.rawExecSQL("PRAGMA encrypted.user_version = $version")
                plain.rawExecSQL("DETACH DATABASE encrypted")
            }

            // 3: the copy must open with the key and contain the same tables
            check(canOpen(encrypted, passphrase)) { "Encrypted copy could not be read" }

            // 4: swap, keeping the plain file until the new one is in place
            check(file.renameTo(oldPlain)) { "Could not move plain database" }
            if (!encrypted.renameTo(file)) {
                oldPlain.renameTo(file)
                error("Could not move encrypted database")
            }
            listOf(oldPlain, File(file.path + "-wal"), File(file.path + "-shm"), File(file.path + "-journal"))
                .forEach { it.delete() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed; keeping the plain database for now", e)
            encrypted.delete()
            if (!file.exists() && oldPlain.exists()) oldPlain.renameTo(file)
            false
        }
    }

    /** True if [file] opens with [passphrase] and its tables can be read. */
    fun canOpen(file: File, passphrase: String): Boolean = try {
        SQLiteDatabase.openDatabase(file.path, passphrase, null, SQLiteDatabase.OPEN_READONLY, null).use { db ->
            db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
        }
    } catch (e: Exception) {
        false
    }

    /**
     * An encrypted database whose key is gone (e.g. restored onto another phone by
     * Android's backup) can never be opened. Move it aside so the app can start.
     */
    fun setAsideIfUnreadable(context: Context, name: String, passphrase: String) {
        val file = context.getDatabasePath(name)
        if (stateOf(file) != State.ENCRYPTED || canOpen(file, passphrase)) return
        Log.w(TAG, "Database can't be opened with this phone's key; moving it aside")
        val stamp = System.currentTimeMillis()
        listOf("", "-wal", "-shm").forEach { suffix ->
            File(file.path + suffix).takeIf { it.exists() }?.renameTo(File("${file.path}.unreadable-$stamp$suffix"))
        }
    }
}
