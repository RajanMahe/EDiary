package com.aburv.kurippidu.ui

import android.content.Context
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.aburv.kurippidu.AppLockState
import com.aburv.kurippidu.BackupPreferences
import com.aburv.kurippidu.LockMode
import com.aburv.kurippidu.MainActivity
import com.aburv.kurippidu.ThemeMode
import com.aburv.kurippidu.data.DiaryOwnerEntity
import com.aburv.kurippidu.testutil.TEST_TIMEOUT_MS
import com.aburv.kurippidu.testutil.TestRepository
import com.aburv.kurippidu.testutil.tap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.aburv.kurippidu.testutil.eventually
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var data: TestRepository
    private var scenario: ActivityScenario<MainActivity>? = null

    // Fake fingerprint prompt: records each request so the test can answer it
    private val promptRequests = mutableListOf<Pair<() -> Unit, () -> Unit>>()
    private val realAuthenticate = AppLockState.authenticate

    @Before
    fun setUp() {
        data = TestRepository()
        AppLockState.authenticate = { _, onSuccess, onError -> promptRequests += onSuccess to onError }
        setUnlocked(false) // as after a cold start
        AppLockState.isLockTemporarilyDisabled = false
        runBlocking {
            BackupPreferences.setThemeMode(context, ThemeMode.LIGHT)
            BackupPreferences.setOnboardingDone(context, true) // most tests skip the welcome
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
        data.close()
        setUnlocked(false)
        AppLockState.authenticate = realAuthenticate
    }

    /**
     * Changes the shared lock state so Compose sees it at once. A plain write
     * waits for Compose's global apply step, which (on the JVM) is tied to the
     * first test's main thread, so later tests would wait forever.
     */
    private fun setUnlocked(value: Boolean) = Snapshot.withMutableSnapshot { AppLockState.isUnlocked = value }

    private fun launch(lock: LockMode) {
        runBlocking { BackupPreferences.setLockMode(context, lock) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitForText(text: String) = compose.waitUntil(TEST_TIMEOUT_MS) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    /* ---------------- WELCOME ---------------- */

    @Test
    fun `a new user sees the welcome, can skip the lock and lands on home`() {
        runBlocking { BackupPreferences.setOnboardingDone(context, false) }
        launch(LockMode.OFF)

        waitForText("Welcome to Kurippidu")
        compose.onNodeWithText("Stays on your device").assertExists()
        compose.onNodeWithText("Lock with fingerprint").assertExists()
        compose.onNodeWithText("Backup anytime").assertExists()

        compose.onNodeWithText("Next").tap()
        waitForText("Protect your diary?")
        assertEquals(0, promptRequests.size) // explained first; no prompt yet

        compose.onNodeWithText("Not now").tap()
        waitForText("My Diaries")
        assertEquals(true, runBlocking { BackupPreferences.onboardingDone(context).first() })
        assertEquals(LockMode.OFF, runBlocking { BackupPreferences.lockMode(context).first() })
    }

    @Test
    fun `turning the lock on from the welcome asks once and stays unlocked`() {
        runBlocking { BackupPreferences.setOnboardingDone(context, false) }
        launch(LockMode.OFF)
        waitForText("Welcome to Kurippidu")
        compose.onNodeWithText("Next").tap()

        compose.onNodeWithText("Turn on lock").tap()
        assertEquals(1, promptRequests.size)
        compose.runOnUiThread { promptRequests.last().first() } // fingerprint accepted

        waitForText("My Diaries")
        compose.onNodeWithText("Unlock").assertDoesNotExist()
        eventually { assertEquals(LockMode.BIOMETRIC, runBlocking { BackupPreferences.lockMode(context).first() }) }
    }

    @Test
    fun `a cancelled fingerprint on the welcome explains and stays`() {
        runBlocking { BackupPreferences.setOnboardingDone(context, false) }
        launch(LockMode.OFF)
        waitForText("Welcome to Kurippidu")
        compose.onNodeWithText("Next").tap()
        compose.onNodeWithText("Turn on lock").tap()
        compose.runOnUiThread { promptRequests.last().second() }

        waitForText("Lock not turned on. You can try again or skip.")
        compose.onNodeWithText("Protect your diary?").assertExists()
    }

    @Test
    fun `people updating from an older version skip the welcome`() {
        runBlocking {
            BackupPreferences.setOnboardingDone(context, false)
            val id = data.dao.insertDiary(DiaryOwnerEntity(title = "Old diary")).toInt()
            data.repository.saveEntry(id, LocalDate.of(2025, 5, 1), "Written last year")
        }
        launch(LockMode.OFF)

        waitForText("Old diary")
        compose.onNodeWithText("Welcome to Kurippidu").assertDoesNotExist()
        eventually { assertEquals(true, runBlocking { BackupPreferences.onboardingDone(context).first() }) }
    }

    @Test
    fun `with the lock off the app opens on the home screen`() {
        launch(LockMode.OFF)
        waitForText("My Diaries")
        compose.onNodeWithText("Unlock").assertDoesNotExist()
    }

    @Test
    fun `with the lock on, a cold start asks for the fingerprint and hides the diaries`() {
        runBlocking { data.dao.insertDiary(DiaryOwnerEntity(title = "Secret diary")) }
        launch(LockMode.BIOMETRIC)

        waitForText("Unlock")
        compose.onNodeWithText("Secret diary").assertDoesNotExist()
        assertEquals(1, promptRequests.size) // asked straight away

        // Fingerprint accepted
        compose.runOnUiThread { promptRequests.last().first() }
        waitForText("Secret diary")
        compose.onNodeWithText("Unlock").assertDoesNotExist()
    }

    @Test
    fun `cancelled prompt leaves an Unlock button that asks again`() {
        launch(LockMode.BIOMETRIC)
        waitForText("Unlock")

        // User cancels the prompt
        compose.runOnUiThread { promptRequests.last().second() }
        compose.onNodeWithText("Unlock").tap()
        assertEquals(2, promptRequests.size)

        compose.runOnUiThread { promptRequests.last().first() }
        waitForText("My Diaries")
    }

    private fun secureFlagSet(): Boolean {
        var secure = false
        scenario!!.onActivity {
            secure = it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0
        }
        return secure
    }

    @Test
    fun `lock on hides the app in Recents and blocks screenshots`() {
        launch(LockMode.BIOMETRIC)
        waitForText("Unlock")
        assertEquals(true, secureFlagSet())
    }

    @Test
    fun `lock off allows screenshots`() {
        launch(LockMode.OFF)
        waitForText("My Diaries")
        assertEquals(false, secureFlagSet())
    }

    @Test
    fun `unlocking returns to the screen you were on`() {
        val diaryId = runBlocking { data.dao.insertDiary(DiaryOwnerEntity(title = "Daily")).toInt() }
        runBlocking { BackupPreferences.setLastOpenedDate(context, diaryId, LocalDate.now().toString()) }
        setUnlocked(true)
        launch(LockMode.BIOMETRIC)

        // Open the diary
        waitForText("Daily")
        compose.onNodeWithText("Daily").tap()
        compose.waitUntil(TEST_TIMEOUT_MS) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Add Todo")).fetchSemanticsNodes().isNotEmpty()
        }

        // App goes to the background and locks, then is unlocked again
        compose.runOnUiThread { setUnlocked(false) }
        waitForText("Unlock")
        compose.runOnUiThread { setUnlocked(true) }

        // Still inside the diary, not back on the home screen
        compose.waitUntil(TEST_TIMEOUT_MS) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Add Todo")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("My Diaries").assertDoesNotExist()
    }
}
