package com.aburv.kurippidu

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Lives as long as the app process: survives screen rotation,
// resets to "locked" when the app is killed (cold start).
object AppLockState {
    var isUnlocked by mutableStateOf(false)
    var isLockTemporarilyDisabled = false
}
