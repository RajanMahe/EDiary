package com.example.diary

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.diary.ui.theme.DiaryTheme
import kotlinx.coroutines.launch
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect




class MainActivity : FragmentActivity() {

    private var isUnlocked by mutableStateOf(true)
    private var lockMode by mutableStateOf(LockMode.OFF)
    private var isAuthInProgress by mutableStateOf(false)
    private var isLockTemporarilyDisabled by mutableStateOf(false)

    private var themeMode by mutableStateOf(ThemeMode.LIGHT)






    override fun onCreate(savedInstanceState: Bundle?) {

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        if (lockMode == LockMode.BIOMETRIC &&
                            !isLockTemporarilyDisabled
                            ) {
                            isUnlocked = false
                            isAuthInProgress = false   // ⭐ CRITICAL FIX

                        }
                    }

                    Lifecycle.Event.ON_START -> {
                        if (lockMode == LockMode.BIOMETRIC &&
                            !isUnlocked &&
                            !isAuthInProgress
                        ) {
                            isAuthInProgress = true
                            BiometricAuthHelper(
                                activity = this@MainActivity,
                                onSuccess = {
                                    isUnlocked = true
                                    isAuthInProgress = false
                                },
                                onError = {
                                    isAuthInProgress = false
                                }
                            ).authenticate()
                        }
                    }





                    else -> Unit
                }
            }
        )



        super.onCreate(savedInstanceState)

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
            BackupPreferences.lockMode(this@MainActivity).collect {
                lockMode = it
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
                Surface (
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                    ){
//                    DisposableEffect(Unit) {
//                        val observer = LifecycleEventObserver { _, event ->
//                            if (
//                                event == Lifecycle.Event.ON_START &&
//                                lockMode == LockMode.BIOMETRIC &&
//                                !isUnlocked &&
//                                !isAuthInProgress
//                            ) {
//                                isAuthInProgress = true
//
//                                BiometricAuthHelper(
//                                    activity = this@MainActivity,
//                                    onSuccess = {
//                                        isUnlocked = true
//                                        isAuthInProgress = false
//                                    },
//                                    onError = {
//                                        isAuthInProgress = false
//                                    }
//                                ).authenticate()
//                            }
//                        }
//
//                        lifecycle.addObserver(observer)
//
//                        onDispose {
//                            lifecycle.removeObserver(observer)
//                        }
//                    }



                    when {
                        lockMode == LockMode.OFF || isUnlocked -> {
                            DiaryScreen(
                                lockMode = lockMode,
                                themeMode = themeMode,
                                onExportStarted = {
                                    isLockTemporarilyDisabled = true
                                },
                                onExportFinished = {
                                    isLockTemporarilyDisabled = false
                                }
                            )
                        }
                        else -> Box(Modifier.fillMaxSize())
                    }

                }
            }
        }
    }


}
