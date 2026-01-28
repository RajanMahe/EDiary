package com.example.diary

import androidx.compose.runtime.Composable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text




@Composable
fun AppLockToggle(
    currentMode: LockMode,
    onEnableRequested: () -> Unit,
    onDisableRequested: () -> Unit
) {
    val isEnabled = currentMode == LockMode.BIOMETRIC

    ListItem(
        headlineContent = { Text("App Lock") },
        supportingContent = {
            Text(
                if (isEnabled)
                    "Biometric / Face enabled"
                else
                    "Off"
            )
        },
        trailingContent = {
            Switch(
                checked = isEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        onEnableRequested()
                    } else {
                        onDisableRequested()
                    }
                }
            )
        }
    )
}
