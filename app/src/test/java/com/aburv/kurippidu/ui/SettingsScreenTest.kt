package com.aburv.kurippidu.ui

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.aburv.kurippidu.BackupPreferences
import com.aburv.kurippidu.DiaryFontSize
import com.aburv.kurippidu.LockMode
import com.aburv.kurippidu.SettingsScreen
import com.aburv.kurippidu.ThemeMode
import com.aburv.kurippidu.testutil.TEST_TIMEOUT_MS
import com.aburv.kurippidu.testutil.eventually
import com.aburv.kurippidu.testutil.tap
import com.aburv.kurippidu.ui.theme.DiaryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Tall screen so the whole list is drawn without scrolling
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var backPresses = 0
    private var backupOpened = 0
    private var lockAnswer = true
    private var lockAsked = 0

    @Before
    fun setUp() {
        runBlocking {
            BackupPreferences.setThemeMode(context, ThemeMode.LIGHT)
            BackupPreferences.setFontSize(context, DiaryFontSize.MEDIUM)
            BackupPreferences.setLockMode(context, LockMode.OFF)
            BackupPreferences.setCoachTips(context, false)
        }
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                SettingsScreen(
                    onBack = { backPresses++ },
                    onOpenBackup = { backupOpened++ },
                    onEnableLock = { onResult -> lockAsked++; onResult(lockAnswer) }
                )
            }
        }
    }

    private fun waitForText(text: String) = compose.waitUntil(TEST_TIMEOUT_MS) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `shows every section`() {
        listOf("Appearance", "Theme", "Text size", "Privacy", "App Lock", "Backup", "Backup & Restore", "Version")
            .forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun `theme can follow the phone`() {
        compose.onNodeWithText("Theme").tap()
        compose.onNodeWithText("Follow phone").tap()
        eventually { assertEquals(ThemeMode.SYSTEM, runBlocking { BackupPreferences.themeMode(context).first() }) }
        waitForText("Follow phone")
    }

    @Test
    fun `dark theme can be chosen`() {
        compose.onNodeWithText("Theme").tap()
        compose.onNodeWithText("Dark").tap()
        eventually { assertEquals(ThemeMode.DARK, runBlocking { BackupPreferences.themeMode(context).first() }) }
    }

    @Test
    fun `text size can be changed`() {
        compose.onNodeWithText("Text size").tap()
        compose.onNodeWithText("Large").tap()
        eventually { assertEquals(DiaryFontSize.LARGE, runBlocking { BackupPreferences.fontSize(context).first() }) }
    }

    @Test
    fun `turning the lock on asks for the fingerprint first`() {
        compose.onAllNodes(isToggleable())[0].tap() // app lock
        assertEquals(1, lockAsked)
        eventually { assertEquals(LockMode.BIOMETRIC, runBlocking { BackupPreferences.lockMode(context).first() }) }
        waitForText("App lock enabled")
    }

    @Test
    fun `a failed fingerprint leaves the lock off`() {
        lockAnswer = false
        compose.onAllNodes(isToggleable())[0].tap() // app lock
        waitForText("Authentication failed")
        assertEquals(LockMode.OFF, runBlocking { BackupPreferences.lockMode(context).first() })
    }

    @Test
    fun `turning the lock off`() {
        runBlocking { BackupPreferences.setLockMode(context, LockMode.BIOMETRIC) }
        waitForText("Biometric / Face enabled")
        compose.onAllNodes(isToggleable())[0].tap() // app lock
        eventually { assertEquals(LockMode.OFF, runBlocking { BackupPreferences.lockMode(context).first() }) }
    }

    @Test
    fun `weekly coach tips can be turned on and off`() {
        org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        // Tips are scheduled with WorkManager
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(
            context, androidx.work.Configuration.Builder().setExecutor(androidx.work.testing.SynchronousExecutor()).build()
        )
        compose.onNodeWithText("Weekly coach tips").assertExists()
        compose.onAllNodes(isToggleable())[1].tap()
        eventually { assertEquals(true, runBlocking { BackupPreferences.coachTips(context).first() }) }
        compose.onAllNodes(isToggleable())[1].tap()
        eventually { assertEquals(false, runBlocking { BackupPreferences.coachTips(context).first() }) }
    }

    @Test
    fun `backup opens the backup screen`() {
        compose.onNodeWithText("Backup & Restore").tap()
        assertEquals(1, backupOpened)
    }
}
