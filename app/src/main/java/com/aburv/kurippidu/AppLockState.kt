package com.aburv.kurippidu

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity

// Lives as long as the app process: survives screen rotation,
// resets to "locked" when the app is killed (cold start).
object AppLockState {
    var isUnlocked by mutableStateOf(false)
    var isLockTemporarilyDisabled = false

    /**
     * Shows the fingerprint / screen-lock prompt: (activity, onSuccess, onError).
     * Tests replace it, because the real system dialog can't run on the JVM.
     */
    @set:VisibleForTesting
    var authenticate: (FragmentActivity, () -> Unit, () -> Unit) -> Unit = { activity, onSuccess, onError ->
        BiometricAuthHelper(activity, onSuccess, onError).authenticate()
    }
}
