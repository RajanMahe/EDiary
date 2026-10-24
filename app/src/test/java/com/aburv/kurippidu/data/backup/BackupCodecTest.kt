package com.aburv.kurippidu.data.backup

import com.aburv.kurippidu.data.ParsedDiary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupCodecTest {

    private val sample = FullBackup(
        exportedAt = "2026-01-10T09:30:00",
        settings = BackupSettings(themeMode = "DARK", lockMode = "BIOMETRIC"),
        diaries = listOf(
            BackupDiary(
                title = "Gym",
                createdAt = 123,
                entries = listOf(BackupEntry(date = "2026-01-05", content = "Leg day")),
                todos = listOf(
                    BackupTodo(key = 1, title = "Stretch", date = "2026-01-01", type = "RECURRING",
                        startDate = "2026-01-01", endDate = "2026-01-31"),
                    BackupTodo(key = 2, title = "Stretch", date = "2026-01-05", type = "DAILY_OVERRIDE",
                        isDone = true, parentKey = 1)
                )
            )
        )
    )

    @Test
    fun `full backup survives a round trip`() {
        val text = BackupCodec.encode(sample)
        assertTrue(text.contains("\"version\": 3"))
        assertEquals(ParsedBackup.Full(sample), BackupCodec.decode(text))
    }

    @Test
    fun `old version 1 files still open`() {
        val v1 = """[{"date":"2026-01-05","content":"First"},{"date":"2026-01-06","content":"Second"}]"""
        assertEquals(
            ParsedBackup.SingleDiary(
                listOf(
                    ParsedDiary(LocalDate.of(2026, 1, 5), "First"),
                    ParsedDiary(LocalDate.of(2026, 1, 6), "Second")
                )
            ),
            BackupCodec.decode(v1)
        )
    }

    @Test
    fun `unknown extra fields from newer files are ignored`() {
        val text = """{"app":"kurippidu","version":2,"exportedAt":"x","futureField":true,
            "diaries":[{"title":"A","entries":[{"date":"2026-01-01","content":"c","stickers":[1]}]}]}"""
        val parsed = BackupCodec.decode(text) as ParsedBackup.Full
        assertEquals("c", parsed.backup.diaries.single().entries.single().content)
    }

    @Test
    fun `newer major version asks to update the app`() {
        val e = assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("""{"app":"kurippidu","version":99,"exportedAt":"x"}""")
        }
        assertTrue(e.message!!.contains("newer version"))
    }

    @Test
    fun `broken files are rejected before anything is restored`() {
        listOf(
            "not json",
            "42",
            """{"exportedAt":"x"}""",                                      // no version
            """{"app":"other","version":2,"exportedAt":"x"}""",            // other app
            """[{"date":"2026-13-45","content":"x"}]""",                   // bad v1 date
            """[{"date":"2026-01-01"}]""",                                  // v1 missing content
            """{"version":2,"exportedAt":"x","diaries":[{"title":"","entries":[]}]}""",
            """{"version":2,"exportedAt":"x","diaries":[{"title":"A","entries":[{"date":"nope","content":"c"}]}]}""",
            """{"version":2,"exportedAt":"x","diaries":[{"title":"A","todos":[{"key":2,"title":"t","date":"2026-01-01","type":"DAILY_OVERRIDE","parentKey":9}]}]}"""
        ).forEach { broken ->
            assertThrows("for $broken", BackupFormatException::class.java) { BackupCodec.decode(broken) }
        }
    }
}
