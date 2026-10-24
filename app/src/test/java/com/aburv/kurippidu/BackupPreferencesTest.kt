package com.aburv.kurippidu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupPreferencesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `lock mode is saved and read back`() = runBlocking {
        BackupPreferences.setLockMode(context, LockMode.BIOMETRIC)
        assertEquals(LockMode.BIOMETRIC, BackupPreferences.lockMode(context).first())

        BackupPreferences.setLockMode(context, LockMode.OFF)
        assertEquals(LockMode.OFF, BackupPreferences.lockMode(context).first())
    }

    @Test
    fun `theme mode is saved and read back`() = runBlocking {
        BackupPreferences.setThemeMode(context, ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, BackupPreferences.themeMode(context).first())

        BackupPreferences.setThemeMode(context, ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, BackupPreferences.themeMode(context).first())
    }

    @Test
    fun `last opened date is remembered per diary`() = runBlocking {
        BackupPreferences.setLastOpenedDate(context, 101, "2026-01-05")
        BackupPreferences.setLastOpenedDate(context, 102, "2026-02-10")

        assertEquals("2026-01-05", BackupPreferences.lastOpenedDate(context, 101).first())
        assertEquals("2026-02-10", BackupPreferences.lastOpenedDate(context, 102).first())
        assertEquals(null, BackupPreferences.lastOpenedDate(context, 999).first())
    }

    @Test
    fun `last backup time is saved`() = runBlocking {
        BackupPreferences.setLastBackupTime(context, 1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, BackupPreferences.lastBackupTime(context).first())
    }
}
