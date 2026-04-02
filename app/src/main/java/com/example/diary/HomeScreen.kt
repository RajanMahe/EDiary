package com.example.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diary.data.DiaryOwnerEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDiarySelected: (diaryId: Int) -> Unit,
    lockMode: LockMode,
    themeMode: ThemeMode,
    viewModel: HomeViewModel = viewModel()
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newDiaryTitle by remember { mutableStateOf("") }

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
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
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
                        onClick = { onDiarySelected(diary.diaryId) },
                        onRename = {
                            diaryToRename = diary
                            renameText = diary.title
                        },
                        onDelete = { diaryToDelete = diary }
                    )
                }
                item { Spacer(Modifier.height(72.dp)) } // FAB clearance
            }
        }
    }

    // ── Create diary dialog ──
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newDiaryTitle = ""
            },
            title = { Text("New diary") },
            text = {
                OutlinedTextField(
                    value = newDiaryTitle,
                    onValueChange = { newDiaryTitle = it },
                    label = { Text("Diary name") },
                    placeholder = { Text("e.g. Workout, Food, Daily") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createDiary(newDiaryTitle)
                        showCreateDialog = false
                        newDiaryTitle = ""
                    },
                    enabled = newDiaryTitle.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    newDiaryTitle = ""
                }) { Text("Cancel") }
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
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
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
                text = diaryEmoji(diary.title),
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