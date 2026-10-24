package com.aburv.kurippidu

import android.net.Uri
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aburv.kurippidu.backup.BackupViewModel
import com.aburv.kurippidu.data.backup.BackupFormatException
import com.aburv.kurippidu.data.backup.FullBackup
import com.aburv.kurippidu.data.backup.ParsedBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Backup & Restore.
 *
 * Full backup and automatic backup cover every diary. When opened from a diary
 * ([viewModel] not null) it also offers that diary's Markdown export and text import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: DiaryViewModel?,
    onExportStarted: () -> Unit,
    onExportFinished: () -> Unit,
    onBack: () -> Unit,
    backupViewModel: BackupViewModel = viewModel()
) {
    // System Back closes this screen instead of leaving the diary
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current

    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var pendingFullRestore by remember { mutableStateOf<FullBackup?>(null) }
    var pendingDiaryRestore by remember { mutableStateOf<String?>(null) }
    var isRestoring by remember { mutableStateOf(false) }
    var showFrequencyDialog by remember { mutableStateOf(false) }
    var totalEntries by remember { mutableStateOf<Int?>(null) }

    fun readText(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Could not open file")

    /* ---------------- PICKERS ---------------- */

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        onExportFinished() // re-enable the lock (picked or cancelled)
        if (uri == null || isRestoring) return@rememberLauncherForActivityResult

        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { readText(uri) }
                when (val parsed = backupViewModel.parse(text)) {
                    is ParsedBackup.Full -> pendingFullRestore = parsed.backup
                    is ParsedBackup.SingleDiary ->
                        if (viewModel != null) pendingDiaryRestore = text
                        else snackbarHostState.showSnackbar(
                            "This is a single-diary backup. Open a diary and restore it there."
                        )
                }
            } catch (e: BackupFormatException) {
                snackbarHostState.showSnackbar(e.message ?: "Invalid backup file")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Invalid backup file")
            }
        }
    }

    val importTxtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        onExportFinished()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            pendingImportText = runCatching { withContext(Dispatchers.IO) { readText(uri) } }.getOrNull()
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        onExportFinished()
        if (uri != null) backupViewModel.chooseFolder(uri)
    }

    var pendingExport by remember { mutableStateOf<String?>(null) }

    fun writeExport(uri: Uri?) {
        val content = pendingExport
        pendingExport = null
        if (uri == null || content == null) {
            onExportFinished() // cancelled
            return
        }
        scope.launch {
            val message = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                        ?: error("Could not open file")
                }
                backupViewModel.recordManualBackup()
                "Backup saved"
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            } finally {
                // Re-enable the lock now, not after the message disappears (~4 s)
                onExportFinished()
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { writeExport(it) }

    val exportMdLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { writeExport(it) }

    LaunchedEffect(viewModel) {
        totalEntries = viewModel?.getTotalEntryCount()
    }

    LaunchedEffect(viewModel) {
        viewModel?.backupEvents?.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    is DiaryViewModel.BackupEvent.Success -> event.message
                    is DiaryViewModel.BackupEvent.Error -> event.message
                }
            )
        }
    }

    /* ---------------- SCREEN ---------------- */

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            /* ---------- FULL BACKUP ---------- */

            item { SectionHeader("Full backup") }
            item {
                SettingsItem(
                    title = "Export all diaries",
                    subtitle = "Every diary, task and setting in one file",
                    onClick = {
                        scope.launch {
                            onExportStarted() // keep the lock off while the picker is open
                            pendingExport = backupViewModel.exportAll()
                            exportJsonLauncher.launch("kurippidu_backup_${LocalDate.now()}.json")
                        }
                    }
                )
            }
            item {
                SettingsItem(
                    title = "Restore from backup",
                    subtitle = if (isRestoring) "Restoring…" else "Open a backup file",
                    onClick = if (isRestoring) null else {
                        {
                            onExportStarted()
                            restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                        }
                    }
                )
            }

            /* ---------- AUTOMATIC BACKUP ---------- */

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Automatic backup") }
            item {
                SettingsItem(
                    title = "Backup folder",
                    subtitle = backupViewModel.folderName ?: "Not chosen. Tap to pick a folder.",
                    onClick = {
                        onExportStarted()
                        folderLauncher.launch(null)
                    }
                )
            }
            item {
                SettingsItem(
                    title = "How often",
                    subtitle = backupViewModel.frequency.label(),
                    onClick = if (backupViewModel.folderName == null) null else {
                        { showFrequencyDialog = true }
                    }
                )
            }
            if (backupViewModel.folderName != null) {
                item {
                    SettingsItem(
                        title = "Back up now",
                        subtitle = "Save a backup to the folder straight away",
                        onClick = {
                            scope.launch {
                                val result = backupViewModel.backUpNow()
                                snackbarHostState.showSnackbar(
                                    result.fold({ "Saved $it" }, { it.message ?: "Backup failed" })
                                )
                            }
                        }
                    )
                }
            }
            item {
                val status = backupViewModel.status
                SettingsItem(
                    title = "Last automatic backup",
                    subtitle = when {
                        status.lastError != null -> "Failed: ${status.lastError}"
                        status.lastSuccess != null -> formatTime(status.lastSuccess)
                        else -> "Not yet"
                    }
                )
            }

            /* ---------- THIS DIARY ---------- */

            if (viewModel != null) {
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item { SectionHeader("This diary") }
                item {
                    SettingsItem(
                        title = "Export as Markdown",
                        subtitle = "Human-readable",
                        onClick = {
                            scope.launch {
                                onExportStarted()
                                pendingExport = viewModel.exportAsMarkdown()
                                exportMdLauncher.launch("kurippidu_${LocalDate.now()}.md")
                            }
                        }
                    )
                }
                item {
                    SettingsItem(
                        title = "Import from TXT",
                        subtitle = "Date-based text import",
                        onClick = {
                            onExportStarted()
                            importTxtLauncher.launch(arrayOf("text/plain"))
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
                            } else {
                                scope.launch { snackbarHostState.showSnackbar("Clipboard empty") }
                            }
                        }
                    )
                }
                item {
                    SettingsItem(title = "Total entries", subtitle = totalEntries?.toString() ?: "—")
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsItem(
                    title = "Last backup",
                    subtitle = backupViewModel.lastManualBackup?.let(::formatTime) ?: "—"
                )
            }
        }
    }

    /* ---------------- DIALOGS ---------------- */

    if (viewModel != null && pendingImportText != null) {
        ImportPreviewSheet(
            text = pendingImportText!!,
            viewModel = viewModel,
            onDone = { pendingImportText = null }
        )
    }

    pendingFullRestore?.let { backup ->
        val entries = backup.diaries.sumOf { it.entries.size }
        val tasks = backup.diaries.sumOf { it.todos.size }
        AlertDialog(
            onDismissRequest = { pendingFullRestore = null },
            title = { Text("Restore backup") },
            text = {
                Text(
                    "${backup.diaries.size} diaries, $entries entries and $tasks tasks will be restored. " +
                            "Diaries with the same name are combined; a day that already has an entry " +
                            "gets the backup's version."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        isRestoring = true
                        val message = try {
                            val summary = backupViewModel.restore(backup)
                            "Restored ${summary.diaries} diaries and ${summary.entries} entries"
                        } catch (e: Exception) {
                            "Restore failed. Nothing was changed."
                        }
                        isRestoring = false
                        pendingFullRestore = null
                        viewModel?.reloadCurrentDate()
                        snackbarHostState.showSnackbar(message)
                    }
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingFullRestore = null }) { Text("Cancel") }
            }
        )
    }

    if (viewModel != null && pendingDiaryRestore != null) {
        AlertDialog(
            onDismissRequest = { pendingDiaryRestore = null },
            title = { Text("Restore backup") },
            text = {
                Text("This older backup holds one diary. Its entries will be added to this diary; a day that already has an entry gets the backup's version.")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        isRestoring = true
                        viewModel.restoreFromJson(pendingDiaryRestore!!, DiaryViewModel.RestoreMode.MERGE)
                        isRestoring = false
                        pendingDiaryRestore = null
                    }
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiaryRestore = null }) { Text("Cancel") }
            }
        )
    }

    if (showFrequencyDialog) {
        AlertDialog(
            onDismissRequest = { showFrequencyDialog = false },
            title = { Text("Automatic backup") },
            text = {
                Column {
                    AutoBackupFrequency.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == backupViewModel.frequency,
                                    onClick = {
                                        backupViewModel.changeFrequency(option)
                                        showFrequencyDialog = false
                                    }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == backupViewModel.frequency, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(option.label())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFrequencyDialog = false }) { Text("Close") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportPreviewSheet(text: String, viewModel: DiaryViewModel, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val dates = remember(text) { viewModel.previewImportDates(text) }
    var showAllDates by remember { mutableStateOf(false) }
    val dateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy") }

    ModalBottomSheet(onDismissRequest = onDone) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Import preview", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Detected ${dates.size} entries")
            Spacer(Modifier.height(12.dp))

            val visible: List<LocalDate?> =
                if (showAllDates || dates.size <= 6) dates
                else dates.take(3) + listOf(null) + dates.takeLast(3)
            visible.forEach { date ->
                Text(
                    if (date == null) "• …" else "• ${date.format(dateFormatter)}",
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            if (!showAllDates && dates.size > 6) {
                TextButton(onClick = { showAllDates = true }) { Text("View all") }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                enabled = dates.isNotEmpty(),
                onClick = {
                    scope.launch {
                        viewModel.importFromTextSafe(text)
                        onDone()
                    }
                }
            ) { Text("Import") }
        }
    }
}

private fun AutoBackupFrequency.label() = when (this) {
    AutoBackupFrequency.OFF -> "Off"
    AutoBackupFrequency.DAILY -> "Every day"
    AutoBackupFrequency.WEEKLY -> "Every week"
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))

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
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    )
}
