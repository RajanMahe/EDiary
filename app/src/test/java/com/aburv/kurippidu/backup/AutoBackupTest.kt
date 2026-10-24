package com.aburv.kurippidu.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.aburv.kurippidu.BackupPreferences
import com.aburv.kurippidu.data.DiaryOwnerEntity
import com.aburv.kurippidu.data.backup.BackupCodec
import com.aburv.kurippidu.data.backup.ParsedBackup
import com.aburv.kurippidu.testutil.TestRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var data: TestRepository
    private lateinit var dir: File

    @Before
    fun setUp() {
        data = TestRepository()
        dir = Files.createTempDirectory("backups").toFile()
        runBlocking {
            val id = data.dao.insertDiary(DiaryOwnerEntity(title = "Daily")).toInt()
            data.repository.saveEntry(id, LocalDate.of(2026, 1, 5), "Hello")
            BackupPreferences.setAutoBackupFolder(context, null)
        }
    }

    @After
    fun tearDown() {
        data.close()
        dir.deleteRecursively()
    }

    private fun runner() = AutoBackupRunner(context, BackupService(context, data.repository))
    private fun folder() = DocumentFile.fromFile(dir)
    private fun at(day: Int) = LocalDateTime.of(2026, 1, day, 21, 0)

    @Test
    fun `writes a dated backup that restores`() = runBlocking {
        val name = runner().run(folder(), at(10))

        assertEquals("kurippidu_auto_2026-01-10.json", name)
        val parsed = BackupCodec.decode(File(dir, name).readText()) as ParsedBackup.Full
        assertEquals("Hello", parsed.backup.diaries.single().entries.single().content)
    }

    @Test
    fun `a second run the same day replaces that day's file`() = runBlocking {
        runner().run(folder(), at(10))
        runner().run(folder(), at(10).plusHours(1))
        assertEquals(listOf("kurippidu_auto_2026-01-10.json"), dir.list()!!.toList())
    }

    @Test
    fun `keeps the newest 7 and never touches other files`() = runBlocking {
        File(dir, "my_notes.txt").writeText("mine")
        File(dir, "kurippidu_backup_2025-12-01.json").writeText("manual")

        (1..10).forEach { runner().run(folder(), at(it)) }

        val autos = dir.list()!!.filter { it.startsWith("kurippidu_auto_") }.sorted()
        assertEquals((4..10).map { "kurippidu_auto_2026-01-%02d.json".format(it) }, autos)
        assertTrue(File(dir, "my_notes.txt").exists())
        assertTrue(File(dir, "kurippidu_backup_2025-12-01.json").exists())
    }

    @Test
    fun `worker backs up to the chosen folder and records success`() = runBlocking {
        BackupPreferences.setAutoBackupFolder(context, Uri.fromFile(dir).toString())

        val result = TestListenableWorkerBuilder<AutoBackupWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(dir.list()!!.any { it.startsWith("kurippidu_auto_") })
        val status = BackupPreferences.autoBackupStatus(context).first()
        assertNull(status.lastError)
        assertTrue(status.lastSuccess != null)
    }

    @Test
    fun `worker without a folder fails and says why`() = runBlocking {
        val result = TestListenableWorkerBuilder<AutoBackupWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals("No backup folder chosen", BackupPreferences.autoBackupStatus(context).first().lastError)
    }

    @Test
    fun `worker reports a folder that disappeared`() = runBlocking {
        BackupPreferences.setAutoBackupFolder(context, Uri.fromFile(File(dir, "gone")).toString())

        val result = TestListenableWorkerBuilder<AutoBackupWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertTrue(BackupPreferences.autoBackupStatus(context).first().lastError!!.contains("Choose it again"))
    }
}
