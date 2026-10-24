package com.aburv.kurippidu

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aburv.kurippidu.ui.theme.DiaryTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var lockMode by mutableStateOf(LockMode.OFF)
    private var lockModeLoaded by mutableStateOf(false)
    private var isAuthInProgress = false

    private var themeMode by mutableStateOf(ThemeMode.LIGHT)

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

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = android.graphics.Color.TRANSPARENT

        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(
                androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                        androidx.core.view.WindowInsetsCompat.Type.navigationBars()
            )
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        lifecycleScope.launch {
            BackupPreferences.lockMode(this@MainActivity).collect { mode ->
                lockMode = mode
                // With the lock on, hide the diary in Recents and block screenshots
                if (mode == LockMode.BIOMETRIC) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                if (!lockModeLoaded) {
                    lockModeLoaded = true
                    if (mode == LockMode.OFF) AppLockState.isUnlocked = true
                    else requestUnlock()
                }
            }
        }
        lifecycleScope.launch {
            BackupPreferences.themeMode(this@MainActivity).collect {
                themeMode = it
            }
        }

        setContent {
            val darkTheme = themeMode == ThemeMode.DARK

            DiaryTheme(darkTheme = darkTheme) {
                SideEffect {
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    // Wait until the saved lock setting is known
                    if (!lockModeLoaded) {
                        Box(Modifier.fillMaxSize())
                        return@Surface
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
                            val navController = rememberNavController()

                            NavHost(
                                navController = navController,
                                startDestination = "home"
                            ) {
                                composable("home") {
                                    HomeScreen(
                                        lockMode = lockMode,
                                        themeMode = themeMode,
                                        onDiarySelected = { diaryId ->
                                            navController.navigate("diary/$diaryId")
                                        }
                                    )
                                }
                                composable("diary/{diaryId}") { backStackEntry ->
                                    val diaryId = backStackEntry.arguments
                                        ?.getString("diaryId")
                                        ?.toIntOrNull() ?: return@composable

                                    // viewModel() here is already scoped to this backstack entry automatically
                                    // Each diary navigation creates an isolated ViewModel instance
                                    DiaryScreen(
                                        diaryId = diaryId,
                                        lockMode = lockMode,
                                        themeMode = themeMode,
                                        onExportStarted = { AppLockState.isLockTemporarilyDisabled = true },
                                        onExportFinished = { AppLockState.isLockTemporarilyDisabled = false },
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }

                        // Drawn on top of the app; hides it and blocks all touches
                        if (isLocked) {
                            LockedScreen(onUnlock = ::requestUnlock)
                        }
                    }
                }
            }
        }
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
