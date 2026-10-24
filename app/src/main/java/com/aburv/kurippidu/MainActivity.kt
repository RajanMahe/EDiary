package com.aburv.kurippidu

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aburv.kurippidu.data.DiaryRepository
import com.aburv.kurippidu.reminders.Reminders
import com.aburv.kurippidu.widget.TodayWidgetData
import com.aburv.kurippidu.ui.theme.DiaryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    private var lockMode by mutableStateOf(LockMode.OFF)
    private var lockModeLoaded by mutableStateOf(false)

    // null until known; true only for brand-new users
    private var showWelcome by mutableStateOf<Boolean?>(null)
    private var isAuthInProgress = false

    private var themeMode by mutableStateOf(ThemeMode.LIGHT)
    private var fontSize by mutableStateOf(DiaryFontSize.MEDIUM)

    // Diary to open, from a reminder notification or the widget
    private var openDiaryRequest by mutableStateOf<Int?>(null)

    // Locks the app when the whole app goes to the background
    private val appLifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP &&
            lockMode == LockMode.BIOMETRIC &&
            !AppLockState.isLockTemporarilyDisabled
        ) {
            AppLockState.isUnlocked = false
            isAuthInProgress = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        openDiaryRequest = diaryIdFrom(intent)

        lifecycleScope.launch {
            BackupPreferences.lockMode(this@MainActivity).collect { mode ->
                lockMode = mode
                // With the lock on, hide the diary in Recents and block screenshots
                if (mode == LockMode.BIOMETRIC) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                // The widget hides task names while the lock is on
                TodayWidgetData.refresh(this@MainActivity)
                if (!lockModeLoaded) {
                    lockModeLoaded = true
                    if (mode == LockMode.OFF) AppLockState.isUnlocked = true
                    else requestUnlock()
                }
            }
        }
        lifecycleScope.launch {
            showWelcome = if (BackupPreferences.onboardingDone(this@MainActivity).first()) {
                false
            } else {
                // People updating from an older version already know the app
                val existingUser = withContext(Dispatchers.IO) {
                    runCatching { DiaryRepository.get(this@MainActivity).hasAnyEntries() }.getOrDefault(false)
                }
                if (existingUser) BackupPreferences.setOnboardingDone(this@MainActivity)
                !existingUser
            }
        }
        lifecycleScope.launch { BackupPreferences.themeMode(this@MainActivity).collect { themeMode = it } }
        lifecycleScope.launch { BackupPreferences.fontSize(this@MainActivity).collect { fontSize = it } }

        setContent {
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            // Draw behind the status and navigation bars. Top bars are dark in both
            // themes, so status icons are always light; the navigation bar follows the theme.
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
                    navigationBarStyle = if (darkTheme) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                )
            }

            DiaryTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalDiaryTextSize provides fontSize.size) {
                    AppContent()
                }
            }
        }
    }

    @Composable
    private fun AppContent() {
        // Wait until the saved lock setting is known
        if (!lockModeLoaded || showWelcome == null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            return
        }

        val isLocked = lockMode == LockMode.BIOMETRIC && !AppLockState.isUnlocked

        Box(Modifier.fillMaxSize()) {
            // The NavHost stays alive while locked, so after unlocking
            // the user is back on the same screen, page and text.
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (isLocked) Modifier.clearAndSetSemantics { } else Modifier)
            ) {
                AppNavHost(rememberNavController())
            }

            // Drawn on top of the app; hides it and blocks all touches
            if (isLocked) {
                LockedScreen(onUnlock = ::requestUnlock)
            }
        }
    }

    @Composable
    private fun AppNavHost(navController: NavHostController) {
        val keepLockOffWhilePicking = { AppLockState.isLockTemporarilyDisabled = true }
        val restoreLock = { AppLockState.isLockTemporarilyDisabled = false }

        // Opened from a reminder or the widget: go to that diary (after the welcome, if shown)
        LaunchedEffect(openDiaryRequest) {
            val id = openDiaryRequest ?: return@LaunchedEffect
            if (showWelcome != true) {
                navController.navigate("diary/$id") { launchSingleTop = true }
            }
            openDiaryRequest = null
        }

        NavHost(
            navController = navController,
            startDestination = if (showWelcome == true) "welcome" else "home"
        ) {
            composable("welcome") {
                WelcomeScreen(
                    onTurnOnLock = { onResult ->
                        confirmOwner { success ->
                            if (success) {
                                // Unlocked right now, so turning the lock on doesn't lock the screen
                                AppLockState.isUnlocked = true
                                lifecycleScope.launch {
                                    BackupPreferences.setLockMode(this@MainActivity, LockMode.BIOMETRIC)
                                }
                            }
                            onResult(success)
                        }
                    },
                    onFinished = {
                        lifecycleScope.launch { BackupPreferences.setOnboardingDone(this@MainActivity) }
                        navController.navigate("home") { popUpTo("welcome") { inclusive = true } }
                    }
                )
            }
            composable("home") {
                HomeScreen(
                    onDiarySelected = { diaryId -> navController.navigate("diary/$diaryId") },
                    onOpenSettings = { navController.navigate("settings") }
                )
            }
            composable("diary/{diaryId}") { backStackEntry ->
                val diaryId = backStackEntry.arguments?.getString("diaryId")?.toIntOrNull() ?: return@composable
                // viewModel() inside is scoped to this back-stack entry: one per opened diary
                DiaryScreen(
                    diaryId = diaryId,
                    onExportStarted = keepLockOffWhilePicking,
                    onExportFinished = restoreLock,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate("settings") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBackup = { navController.navigate("backup") },
                    onEnableLock = { onResult ->
                        confirmOwner { success ->
                            if (success) AppLockState.isUnlocked = true
                            onResult(success)
                        }
                    }
                )
            }
            composable("backup") {
                BackupRestoreScreen(
                    viewModel = null, // all diaries; per-diary tools live in each diary's menu
                    onExportStarted = keepLockOffWhilePicking,
                    onExportFinished = restoreLock,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        diaryIdFrom(intent)?.let { openDiaryRequest = it }
    }

    private fun diaryIdFrom(intent: android.content.Intent?): Int? =
        intent?.getIntExtra(Reminders.EXTRA_DIARY_ID, -1)?.takeIf { it >= 0 }

    /** Asks for fingerprint / screen lock once, e.g. before turning the app lock on. */
    private fun confirmOwner(onResult: (Boolean) -> Unit) {
        AppLockState.authenticate(this, { onResult(true) }, { onResult(false) })
    }

    override fun onStart() {
        super.onStart()
        requestUnlock()
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        super.onDestroy()
    }

    private fun requestUnlock() {
        if (!lockModeLoaded || lockMode != LockMode.BIOMETRIC) return
        if (AppLockState.isUnlocked || isAuthInProgress) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return

        isAuthInProgress = true
        AppLockState.authenticate(
            this,
            {
                AppLockState.isUnlocked = true
                isAuthInProgress = false
            },
            {
                isAuthInProgress = false
            }
        )
    }
}

@Composable
private fun LockedScreen(onUnlock: () -> Unit) {
    val activity = LocalContext.current as? Activity
    val focusManager = LocalFocusManager.current

    // Stop typing into the hidden diary page underneath (losing focus also closes the keyboard)
    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
    }

    // Back while locked leaves the app instead of navigating the hidden screens
    BackHandler { activity?.moveTaskToBack(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow every touch so nothing reaches the app underneath
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = onUnlock) { Text("Unlock") }
    }
}
