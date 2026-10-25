package com.aburv.kurippidu

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.aburv.kurippidu.data.DiaryType
import com.aburv.kurippidu.data.diaryType
import com.aburv.kurippidu.testutil.MainDispatcherRule
import com.aburv.kurippidu.testutil.TestRepository
import com.aburv.kurippidu.testutil.eventually
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private lateinit var data: TestRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        data = TestRepository()
        viewModel = data.viewModel()
    }

    @After
    fun tearDown() = data.close()

    @Test
    fun `starts with no diaries and finishes loading`() {
        eventually { assertFalse(viewModel.isLoading) }
        assertEquals(emptyList<Any>(), viewModel.diaries)
    }

    @Test
    fun `create, rename and delete a diary`() {
        viewModel.createDiary("  Workout  ")
        eventually { assertEquals(listOf("Workout"), viewModel.diaries.map { it.title }) }

        viewModel.renameDiary(viewModel.diaries.single(), " Gym ")
        eventually { assertEquals(listOf("Gym"), viewModel.diaries.map { it.title }) }

        viewModel.deleteDiary(viewModel.diaries.single())
        eventually { assertTrue(viewModel.diaries.isEmpty()) }
    }

    @Test
    fun `a blank name takes the type's name and blank renames are ignored`() {
        viewModel.createDiary("   ", DiaryType.FOOD)
        eventually { assertEquals(listOf("Food diary"), viewModel.diaries.map { it.title }) }
        assertEquals(DiaryType.FOOD, viewModel.diaries.single().diaryType)

        viewModel.renameDiary(viewModel.diaries.single(), "Food")
        eventually { assertEquals(listOf("Food"), viewModel.diaries.map { it.title }) }
        viewModel.renameDiary(viewModel.diaries.single(), "")
        Thread.sleep(100)
        assertEquals(listOf("Food"), viewModel.diaries.map { it.title })
    }

    @Test
    fun `diary type can be chosen and changed`() {
        viewModel.createDiary("Knee", DiaryType.GYM)
        eventually { assertEquals(listOf(DiaryType.GYM), viewModel.diaries.map { it.diaryType }) }

        viewModel.setDiaryType(viewModel.diaries.single(), DiaryType.REHAB)
        eventually { assertEquals(listOf(DiaryType.REHAB), viewModel.diaries.map { it.diaryType }) }
        assertEquals("Knee", viewModel.diaries.single().title)
    }

    @Test
    fun `entry counts update when entries are written elsewhere`() {
        viewModel.createDiary("Daily")
        eventually { assertEquals(1, viewModel.diaries.size) }
        val id = viewModel.diaries.single().diaryId
        assertEquals(0, viewModel.entryCounts[id])

        // Written by the diary screen, not this ViewModel
        runBlocking {
            data.repository.saveEntry(id, LocalDate.of(2026, 1, 1), "One")
            data.repository.saveEntry(id, LocalDate.of(2026, 1, 2), "Two")
        }

        eventually { assertEquals(2, viewModel.entryCounts[id]) }
    }
}
