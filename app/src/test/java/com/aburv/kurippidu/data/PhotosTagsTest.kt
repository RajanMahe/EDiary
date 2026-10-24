package com.aburv.kurippidu.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.aburv.kurippidu.backup.BackupArchive
import com.aburv.kurippidu.backup.BackupService
import com.aburv.kurippidu.data.backup.ParsedBackup
import com.aburv.kurippidu.testutil.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real image decoding
class PhotosTagsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: DiaryDatabase
    private lateinit var repo: DiaryRepository
    private lateinit var photos: File
    private var diaryId = 0
    private val day = LocalDate.of(2026, 1, 5)

    @Before
    fun setUp() = runBlocking {
        db = createTestDatabase()
        photos = tmp.newFolder("attachments")
        repo = DiaryRepository(db, photos)
        diaryId = db.diaryDao().insertDiary(DiaryOwnerEntity(title = "Trips")).toInt()
    }

    /** A real PNG picture of [w] x [h] pixels. */
    private fun picture(w: Int, h: Int): Uri {
        val file = tmp.newFile("pic_${w}x$h.png")
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    /* ---------------- PHOTOS ---------------- */

    @Test
    fun `photo is copied, shrunk and stored as JPEG`() {
        val name = ImageImporter(context, photos).import(picture(4000, 1000), maxSize = 2048)

        val stored = File(photos, name)
        assertTrue(name.endsWith(".jpg"))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(stored.path, bounds)
        assertEquals(2048, bounds.outWidth)
        assertEquals(512, bounds.outHeight)
    }

    @Test
    fun `a file that isn't a picture is refused`() {
        val notImage = tmp.newFile("notes.txt").apply { writeText("hello") }
        val result = runCatching { ImageImporter(context, photos).import(Uri.fromFile(notImage)) }
        assertTrue(result.isFailure)
    }

    @Test
    fun `photos belong to a day and go away with it`() = runBlocking {
        val name = ImageImporter(context, photos).import(picture(100, 100))
        repo.addAttachment(diaryId, day, name)

        val attached = repo.observeAttachments(diaryId, day).first()
        assertEquals(listOf(name), attached.map { it.fileName })
        assertEquals("", repo.getEntry(diaryId, day)!!.content) // the day was created for it

        repo.deleteAttachment(attached.single())
        assertEquals(emptyList<AttachmentEntity>(), repo.observeAttachments(diaryId, day).first())
        assertFalse(File(photos, name).exists())
    }

    @Test
    fun `deleting a diary deletes its photo files`() = runBlocking {
        val name = ImageImporter(context, photos).import(picture(50, 50))
        repo.addAttachment(diaryId, day, name)

        repo.deleteDiary(db.diaryDao().getDiaryById(diaryId)!!)

        assertFalse(File(photos, name).exists())
    }

    @Test
    fun `backup zip carries photos to a new phone`() = runBlocking {
        val name = ImageImporter(context, photos).import(picture(300, 200))
        repo.saveEntry(diaryId, day, "Beach day")
        repo.addAttachment(diaryId, day, name)
        val zip = ByteArrayOutputStream().also { BackupService(context, repo).writeBackupZip(it) }.toByteArray()
        assertTrue(BackupArchive.isZip(zip))

        // New phone: empty database and empty photo folder
        val newPhotos = tmp.newFolder("new_attachments")
        val newRepo = DiaryRepository(createTestDatabase(), newPhotos)
        val service = BackupService(context, newRepo)
        val parsed = service.readBackup(zip) as ParsedBackup.Full
        newRepo.restoreFullBackup(parsed.backup)

        val id = newRepo.firstDiary()!!.diaryId
        val restored = newRepo.observeAttachments(id, day).first().single()
        assertEquals(name, restored.fileName)
        assertTrue(File(newPhotos, name).length() > 0)
        assertEquals("Beach day", newRepo.getEntry(id, day)!!.content)
    }

    @Test
    fun `a crafted zip can't write outside the photo folder`() {
        val zip = ByteArrayOutputStream().also { bytes ->
            java.util.zip.ZipOutputStream(bytes).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("backup.json")); z.write("""{"version":3,"exportedAt":"x"}""".toByteArray())
                z.putNextEntry(java.util.zip.ZipEntry("attachments/../../evil.txt")); z.write("x".toByteArray())
                z.putNextEntry(java.util.zip.ZipEntry("attachments/ok.jpg")); z.write("y".toByteArray())
            }
        }.toByteArray()

        BackupArchive.read(zip, photos)

        assertTrue(File(photos, "ok.jpg").exists())
        assertFalse(File(tmp.root, "evil.txt").exists())
        assertFalse(File(photos.parentFile!!.parentFile, "evil.txt").exists())
    }

    @Test
    fun `older plain JSON backups still read`() {
        val json = """[{"date":"2026-01-05","content":"Old"}]"""
        assertEquals(json, BackupArchive.read(json.toByteArray(), photos))
    }

    /* ---------------- TAGS ---------------- */

    @Test
    fun `tags follow the text`() = runBlocking {
        repo.saveEntry(diaryId, day, "Meeting #Work and #family")
        repo.saveEntry(diaryId, day.plusDays(1), "More #work")
        assertEquals(listOf(TagCount("work", 2), TagCount("family", 1)), repo.observeTags(diaryId).first())

        repo.saveEntry(diaryId, day, "Changed my mind") // tags removed with the words
        assertEquals(listOf(TagCount("work", 1)), repo.observeTags(diaryId).first())
    }

    @Test
    fun `search can be limited to a tag`() = runBlocking {
        repo.saveEntry(diaryId, day, "Gym session #health")
        repo.saveEntry(diaryId, day.plusDays(1), "Gym bag lost")
        repo.saveEntry(diaryId, day.plusDays(2), "Doctor visit #health")

        assertEquals(2, repo.search(diaryId, "Gym").size)
        assertEquals(listOf("Gym session #health"), repo.search(diaryId, "Gym", tag = "health").map { it.content })
        assertEquals(2, repo.search(diaryId, "", tag = "health").size)
    }
}
