package com.aburv.kurippidu.data

import com.aburv.kurippidu.data.backup.BackupCodec
import com.aburv.kurippidu.data.backup.BackupSettings
import com.aburv.kurippidu.data.backup.ParsedBackup
import com.aburv.kurippidu.data.progress.ParsedSet
import com.aburv.kurippidu.data.progress.Side
import com.aburv.kurippidu.testutil.createTestDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutRepositoryTest {

    private lateinit var db: DiaryDatabase
    private lateinit var repo: DiaryRepository
    private var gym = 0
    private val day = LocalDate.of(2026, 1, 5)

    @Before
    fun setUp() = runBlocking {
        db = createTestDatabase()
        repo = DiaryRepository(db)
        gym = db.diaryDao().insertDiary(DiaryOwnerEntity(title = "Gym", type = DiaryType.GYM.name)).toInt()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `unreviewed day shows what was read, confirming saves the edited sets`() = runBlocking {
        repo.saveEntry(gym, day, "Squat 20 kg 10 rep 3 set\n12.5 - 15\nFelt strong today and happy")
        val read = repo.workoutFor(gym, day)
        assertFalse(read.confirmed)
        assertEquals(2, read.sets.size)
        assertEquals(listOf("Felt strong today and happy"), read.notes)

        val fixed = read.sets.map { it.copy(exercise = "Squat") } + ParsedSet("Calf rise", Side.LEFT, reps = 20)
        repo.confirmWorkout(gym, day, fixed)

        val saved = repo.workoutFor(gym, day)
        assertTrue(saved.confirmed)
        assertFalse(saved.changedSinceReview)
        assertEquals(fixed.map { it.copy(sourceLine = "") }, saved.sets) // the source line isn't stored
    }

    @Test
    fun `editing the entry after confirming asks for a new review`() = runBlocking {
        repo.saveEntry(gym, day, "Squat 20 kg 10 rep")
        repo.confirmWorkout(gym, day, repo.workoutFor(gym, day).sets)
        repo.saveEntry(gym, day, "Squat 25 kg 10 rep")

        val after = repo.workoutFor(gym, day)
        assertTrue(after.changedSinceReview)
        assertEquals(20.0, after.sets.single().weightKg) // the confirmed numbers stay until reviewed
    }

    @Test
    fun `history mixes confirmed and read days, oldest first`() = runBlocking {
        repo.saveEntry(gym, day.plusDays(1), "Squat 25 kg 8 rep")
        repo.saveEntry(gym, day, "Squat 20 kg 10 rep")
        repo.saveEntry(gym, day.plusDays(2), "Rest day, just walked around")
        repo.confirmWorkout(gym, day, listOf(ParsedSet("Back squat", weightKg = 20.0, reps = 10)))

        val history = repo.workoutHistory(gym)
        assertEquals(listOf(day, day.plusDays(1)), history.map { it.date })
        assertEquals(listOf(true, false), history.map { it.confirmed })
        assertEquals("Back squat", history.first().sets.single().exercise)
    }

    @Test
    fun `confirmed workouts go with the backup and are deleted with the diary`() = runBlocking {
        repo.saveEntry(gym, day, "Squat 20 kg 10 rep")
        repo.confirmWorkout(gym, day, listOf(ParsedSet("Squat", Side.RIGHT, 22.5, 10, 2)))
        val file = BackupCodec.encode(repo.exportFullBackup(BackupSettings(), "now"))

        val newDb = createTestDatabase()
        val newRepo = DiaryRepository(newDb)
        newRepo.restoreFullBackup((BackupCodec.decode(file) as ParsedBackup.Full).backup)
        val id = newRepo.firstDiary()!!.diaryId
        val restored = newRepo.workoutFor(id, day)
        assertTrue(restored.confirmed)
        assertFalse(restored.changedSinceReview)
        assertEquals(listOf(ParsedSet("Squat", Side.RIGHT, 22.5, 10, 2)), restored.sets)
        newDb.close()

        repo.deleteDiary(db.diaryDao().getDiaryById(gym)!!)
        assertEquals(emptyList<WorkoutSetEntity>(), db.diaryDao().getWorkoutSetsForDiary(gym))
    }

    @Test
    fun `food history reads each day`() = runBlocking {
        val food = db.diaryDao().insertDiary(DiaryOwnerEntity(title = "Food", type = DiaryType.FOOD.name)).toInt()
        repo.saveEntry(food, day, "3 idli, sambar\n2L water")
        repo.saveEntry(food, day.plusDays(1), "Nothing much")
        val history = repo.foodHistory(food)
        assertEquals(listOf(day), history.map { it.first })
        assertEquals(2.0, history.single().second.waterLitres, 0.0)
    }
}
