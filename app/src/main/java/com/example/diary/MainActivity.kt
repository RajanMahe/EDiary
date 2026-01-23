package com.example.diary

import android.os.Bundle
import androidx.activity.compose.setContent

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import com.example.diary.ui.theme.DiaryTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class MainActivity : FragmentActivity() {

    private var isUnlocked by mutableStateOf(false)
    private var lockMode by mutableStateOf(LockMode.BIOMETRIC)


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            BackupPreferences.lockMode(this@MainActivity).collect {
                lockMode = it
            }
        }
        val test: FragmentActivity = this


        setContent {
            DiaryTheme {
                Surface {

                    LaunchedEffect(lockMode, isUnlocked) {
                        if (lockMode == LockMode.BIOMETRIC && !isUnlocked) {
                            BiometricAuthHelper(
                                activity = this@MainActivity,
                                onSuccess = { isUnlocked = true }
                            ).authenticate()
                        }
                    }

                    when {
                        lockMode == LockMode.OFF -> DiaryScreen()
                        isUnlocked -> DiaryScreen()
                        else -> Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (lockMode == LockMode.BIOMETRIC) {
            isUnlocked = false
        }
    }
}
