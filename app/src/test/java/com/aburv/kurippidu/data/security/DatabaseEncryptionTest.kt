package com.aburv.kurippidu.data.security

import com.aburv.kurippidu.data.security.DatabaseEncryption.State
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The encryption itself needs SQLCipher's native library, so it's checked on a
 * device/emulator. This covers how the app decides whether a file is encrypted.
 */
class DatabaseEncryptionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `missing or empty file`() {
        assertEquals(State.MISSING, DatabaseEncryption.stateOf(File(tmp.root, "none")))
        assertEquals(State.MISSING, DatabaseEncryption.stateOf(tmp.newFile("empty")))
    }

    @Test
    fun `plain SQLite file is recognised by its header`() {
        val file = tmp.newFile("plain").apply {
            writeBytes("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(100))
        }
        assertEquals(State.PLAIN, DatabaseEncryption.stateOf(file))
    }

    @Test
    fun `anything else is treated as encrypted`() {
        val file = tmp.newFile("enc").apply { writeBytes(ByteArray(4096) { (it * 31).toByte() }) }
        assertEquals(State.ENCRYPTED, DatabaseEncryption.stateOf(file))
    }
}
