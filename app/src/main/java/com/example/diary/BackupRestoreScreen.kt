package com.example.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import java.time.LocalDate


//-------------------restoreJsonLauncher------------//




//-------------------ImportTextLauncher------------//










@OptIn(ExperimentalMaterial3Api::class)


@Composable
fun BackupRestoreScreen(
    viewModel: DiaryViewModel = viewModel(),





    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current

    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var pendingImportCount by remember { mutableStateOf(0) }

    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    var previewDates by remember {
        mutableStateOf<List<LocalDate>>(emptyList())
    }
    val restoreJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            try {
                val json = context.contentResolver
                    .openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: return@launch

                viewModel.restoreFromJson(
                    json,
                    DiaryViewModel.RestoreMode.OVERWRITE
                )
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Invalid JSON file")
            }
        }
    }






    val importTxtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val text = context.contentResolver
                .openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@launch

            pendingImportText = text
            pendingImportCount = viewModel.previewImportCount(text)
            previewDates = viewModel.previewImportDates(text)
        }
    }

    var totalEntries by remember { mutableStateOf<Int?>(null) }
    var lastBackupTime by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        totalEntries = viewModel.getTotalEntryCount()

        BackupPreferences
            .lastBackupTime(context)
            .collect { time ->
                lastBackupTime = time
            }
    }

        Scaffold(

        snackbarHost = { SnackbarHost(snackbarHostState) },

        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            /* ---------- EXPORT ---------- */

            item {
                SectionHeader("Export")
            }

            item {
                SettingsItem(
                    title = "Export as JSON",
                    subtitle = "Full backup",
                    onClick = {
                        scope.launch {
                            val json = viewModel.exportAsJson()
                            val file = saveToDownloads(context, json, "json")
                            BackupPreferences.setLastBackupTime(
                                context,
                                System.currentTimeMillis()
                            )
                            snackbarHostState.showSnackbar(
                                "Saved to Downloads/EDiary/${file.name}"
                            )
                        }
                    }
                )

            }

            item {
                SettingsItem(
                    title = "Export as Markdown",
                    subtitle = "Human-readable",
                    onClick = {
                        scope.launch {
                            val md = viewModel.exportAsMarkdown()
                            val file = saveToDownloads(context, md, "md")
                            BackupPreferences.setLastBackupTime(
                                context,
                                System.currentTimeMillis()
                            )
                            snackbarHostState.showSnackbar(
                                "Saved to Downloads/EDiary/${file.name}"
                            )
                        }
                    }
                )

            }

            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }

            /* ---------- IMPORT ---------- */

            item {
                SectionHeader("Import")
            }

            item {
                SettingsItem(
                    title = "Restore from JSON",
                    subtitle = "Restore a backup file",
                    onClick = {
                        restoreJsonLauncher.launch(
                            arrayOf("application/json")
                        )
                    }
                )


            }

            item {
                SettingsItem(
                    title = "Import from TXT",
                    subtitle = "Date-based text import",
                    onClick = {
                        importTxtLauncher.launch(
                            arrayOf("text/plain")
                        )
                    }
                )


            }

            item {
                SettingsItem(
                    title = "Import from Clipboard",
                    subtitle = "Paste diary text",
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            pendingImportText = clip
                            pendingImportCount = viewModel.previewImportCount(clip)
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Clipboard empty")
                            }
                        }
                    }
                )

            }

            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }

            /* ---------- STATUS ---------- */

            item {
                SectionHeader("Status")
            }

            item {
                SettingsItem(
                    title = "Total entries",
                    subtitle = totalEntries?.toString() ?: "—"
                )
            }

            item {
                val formatted = lastBackupTime?.let {
                    java.text.SimpleDateFormat(
                        "dd MMM yyyy, hh:mm a",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date(it))
                } ?: "—"

                SettingsItem(
                    title = "Last backup",
                    subtitle = formatted
                )
            }
        }
    }
    if (pendingImportText != null) {
        ModalBottomSheet(
            onDismissRequest = { pendingImportText = null }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Import preview",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(8.dp))

                Text("Found $pendingImportCount entries")

                Spacer(Modifier.height(16.dp))

                Button(
                    enabled = pendingImportCount > 0,
                    onClick = {
                        scope.launch {
                            viewModel.importFromTextSafe(pendingImportText!!)
                            pendingImportText = null
                        }
                    }
                ) {
                    Text("Import")
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.backupEvents.collect { event ->
            val message = when (event) {
                is DiaryViewModel.BackupEvent.Success -> event.message
                is DiaryViewModel.BackupEvent.Error -> event.message
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showRestoreConfirm && pendingRestoreJson != null) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirm = false
                pendingRestoreJson = null
            },
            title = { Text("Restore backup") },
            text = { Text("This will overwrite all existing entries.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.restoreFromJson(
                                pendingRestoreJson!!,
                                DiaryViewModel.RestoreMode.OVERWRITE
                            )
                            showRestoreConfirm = false
                            pendingRestoreJson = null
                        }
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirm = false
                        pendingRestoreJson = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }



}
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
    )
}
