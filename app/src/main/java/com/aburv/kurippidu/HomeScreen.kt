package com.aburv.kurippidu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aburv.kurippidu.data.DiaryOwnerEntity
import com.aburv.kurippidu.data.DiaryType
import com.aburv.kurippidu.data.diaryType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDiarySelected: (diaryId: Int) -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newDiaryTitle by remember { mutableStateOf("") }
    var newDiaryType by remember { mutableStateOf(DiaryType.PLAIN) }

    // For change type
    var diaryToRetype by remember { mutableStateOf<DiaryOwnerEntity?>(null) }

    // For rename
    var diaryToRename by remember { mutableStateOf<DiaryOwnerEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    // For delete confirm
    var diaryToDelete by remember { mutableStateOf<DiaryOwnerEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Diaries",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create diary",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->

            if (viewModel.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading…",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

         else if (viewModel.diaries.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📔", fontSize = 56.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No diaries yet",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tap + to create your first diary",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(viewModel.diaries, key = { it.diaryId }) { diary ->
                    DiaryCard(
                        diary = diary,
                        entryCount = viewModel.entryCounts[diary.diaryId] ?: 0,
                        streak = viewModel.streaks[diary.diaryId] ?: 0,
                        onClick = { onDiarySelected(diary.diaryId) },
                        onRename = {
                            diaryToRename = diary
                            renameText = diary.title
                        },
                        onDelete = { diaryToDelete = diary },
                        onChangeType = { diaryToRetype = diary }
                    )
                }
                item { Spacer(Modifier.height(72.dp)) } // FAB clearance
            }
        }
    }

    // ── Create diary dialog ──
    if (showCreateDialog) {
        fun close() {
            showCreateDialog = false
            newDiaryTitle = ""
            newDiaryType = DiaryType.PLAIN
        }
        AlertDialog(
            onDismissRequest = ::close,
            title = { Text("New diary") },
            text = {
                Column {
                    Text("What is it for?", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    DiaryTypeChips(selected = newDiaryType, onSelect = { newDiaryType = it })
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newDiaryTitle,
                        onValueChange = { newDiaryTitle = it },
                        label = { Text("Diary name") },
                        placeholder = { Text(newDiaryType.suggestedTitle) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createDiary(newDiaryTitle, newDiaryType)
                    close()
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = ::close) { Text("Cancel") }
            }
        )
    }

    // ── Change type dialog ──
    diaryToRetype?.let { diary ->
        AlertDialog(
            onDismissRequest = { diaryToRetype = null },
            title = { Text("Diary type") },
            text = {
                Column {
                    Text(
                        "Changes the templates and progress for \"${diary.title}\". Your entries stay as they are.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    DiaryTypeChips(
                        selected = diary.diaryType,
                        onSelect = {
                            viewModel.setDiaryType(diary, it)
                            diaryToRetype = null
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { diaryToRetype = null }) { Text("Close") }
            }
        )
    }

    // ── Rename dialog ──
    diaryToRename?.let { diary ->
        AlertDialog(
            onDismissRequest = { diaryToRename = null },
            title = { Text("Rename diary") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameDiary(diary, renameText)
                        diaryToRename = null
                    },
                    enabled = renameText.isNotBlank()
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { diaryToRename = null }) { Text("Cancel") }
            }
        )
    }

    // ── Delete confirm dialog ──
    diaryToDelete?.let { diary ->
        AlertDialog(
            onDismissRequest = { diaryToDelete = null },
            title = { Text("Delete diary") },
            text = { Text("\"${diary.title}\" and all its entries will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDiary(diary)
                        diaryToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { diaryToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DiaryCard(
    diary: DiaryOwnerEntity,
    entryCount: Int,
    streak: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onChangeType: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val createdDate = remember(diary.createdAt) {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            .format(Date(diary.createdAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = diary.diaryType.takeIf { it != DiaryType.PLAIN }?.emoji ?: diaryEmoji(diary.title),
                fontSize = 32.sp
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = diary.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$entryCount entries · Created $createdDate",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (streak > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (streak == 1) "🔥 1 day in a row" else "🔥 $streak days in a row",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Change type") },
                        onClick = {
                            showMenu = false
                            onChangeType()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/** Plain / Gym / Rehab / Food as chips; wraps onto two lines on narrow screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryTypeChips(selected: DiaryType, onSelect: (DiaryType) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DiaryType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text("${type.emoji} ${type.label}") }
            )
        }
    }
}

// Auto-pick an emoji based on diary name keywords
private fun diaryEmoji(title: String): String {
    val t = title.lowercase()
    return when {
        t.contains("workout") || t.contains("gym") || t.contains("rehab") || t.contains("exercise") -> "🏋️"
        t.contains("food") || t.contains("meal") || t.contains("diet") || t.contains("eat") -> "🍔"
        t.contains("money") || t.contains("spend") || t.contains("finance") || t.contains("budget") -> "💸"
        t.contains("travel") || t.contains("trip") || t.contains("journey") -> "✈️"
        t.contains("work") || t.contains("office") || t.contains("job") -> "💼"
        t.contains("health") || t.contains("medical") || t.contains("doctor") -> "🏥"
        t.contains("personal") || t.contains("daily") || t.contains("life") -> "📖"
        else -> "📔"
    }
}
