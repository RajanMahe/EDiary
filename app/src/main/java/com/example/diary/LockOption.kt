package com.example.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LockOption(
    label: String,
    mode: LockMode
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()



    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                scope.launch {
                    BackupPreferences.setLockMode(context, mode)
                }
            }
            .padding(vertical = 8.dp)
    ) {
        Text(label)
    }
}
