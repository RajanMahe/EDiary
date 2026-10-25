@file:OptIn(ExperimentalMaterial3Api::class)

package com.aburv.kurippidu

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aburv.kurippidu.reminders.CoachTips
import com.aburv.kurippidu.reminders.Reminders
import kotlinx.coroutines.launch

/**
 * One place for theme, text size, app lock and backup.
 * [extraContent] lets later features (e.g. reminders) add their own section.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
    onEnableLock: (onResult: (Boolean) -> Unit) -> Unit,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val themeMode by BackupPreferences.themeMode(context).collectAsState(initial = ThemeMode.LIGHT)
    val fontSize by BackupPreferences.fontSize(context).collectAsState(initial = DiaryFontSize.MEDIUM)
    val lockMode by BackupPreferences.lockMode(context).collectAsState(initial = LockMode.OFF)
    val autoFrequency by BackupPreferences.autoBackupFrequency(context).collectAsState(initial = AutoBackupFrequency.OFF)
    val coachTips by BackupPreferences.coachTips(context).collectAsState(initial = false)

    // Weekly tips need notifications; turned on only once that's allowed
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            if (granted) CoachTips.setEnabled(context, true)
            else snackbarHostState.showSnackbar("Allow notifications to get weekly tips")
        }
    }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item { Header("Appearance") }
            item {
                Item("Theme", themeMode.label()) { showThemeDialog = true }
            }
            item {
                Item("Text size", fontSize.label) { showFontDialog = true }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Header("Privacy") }
            item {
                AppLockToggle(
                    currentMode = lockMode,
                    onEnableRequested = {
                        onEnableLock { success ->
                            scope.launch {
                                if (success) {
                                    BackupPreferences.setLockMode(context, LockMode.BIOMETRIC)
                                    snackbarHostState.showSnackbar("App lock enabled")
                                } else {
                                    snackbarHostState.showSnackbar("Authentication failed")
                                }
                            }
                        }
                    },
                    onDisableRequested = {
                        scope.launch {
                            BackupPreferences.setLockMode(context, LockMode.OFF)
                            snackbarHostState.showSnackbar("App lock disabled")
                        }
                    }
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Header("Coach") }
            item {
                ListItem(
                    headlineContent = { Text("Weekly coach tips") },
                    supportingContent = { Text("Sunday evening: one tip from each gym, rehab or food diary") },
                    trailingContent = {
                        Switch(
                            checked = coachTips,
                            onCheckedChange = { on ->
                                when {
                                    !on -> scope.launch { CoachTips.setEnabled(context, false) }
                                    Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context) ->
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else -> scope.launch { CoachTips.setEnabled(context, true) }
                                }
                            }
                        )
                    }
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Header("Backup") }
            item {
                Item(
                    "Backup & Restore",
                    "Automatic backup: " + when (autoFrequency) {
                        AutoBackupFrequency.OFF -> "off"
                        AutoBackupFrequency.DAILY -> "every day"
                        AutoBackupFrequency.WEEKLY -> "every week"
                    },
                    onClick = onOpenBackup
                )
            }

            if (extraContent != null) {
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item { Column { extraContent() } }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Item("Version", appVersion(context)) }
        }
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries,
            selected = themeMode,
            label = { it.label() },
            onSelect = { mode ->
                showThemeDialog = false
                scope.launch { BackupPreferences.setThemeMode(context, mode) }
            },
            onDismiss = { showThemeDialog = false }
        )
    }
    if (showFontDialog) {
        ChoiceDialog(
            title = "Text size",
            options = DiaryFontSize.entries,
            selected = fontSize,
            label = { it.label },
            onSelect = { size ->
                showFontDialog = false
                scope.launch { BackupPreferences.setFontSize(context, size) }
            },
            onDismiss = { showFontDialog = false }
        )
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "Follow phone"
}

private fun appVersion(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull() ?: "—"

@Composable
private fun Header(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
internal fun Item(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, onClick = { onSelect(option) })
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label(option))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
