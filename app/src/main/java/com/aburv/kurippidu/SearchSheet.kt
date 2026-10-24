@file:OptIn(ExperimentalMaterial3Api::class)

package com.aburv.kurippidu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SearchSheet(
    onClose: () -> Unit,
    onResultClick: (LocalDate) -> Unit,
    viewModel: DiaryViewModel = viewModel()
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground

                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Search field
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by date or content…") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(Modifier.height(12.dp))

            val resultDateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }

            if (viewModel.searchQuery.isNotBlank() && viewModel.searchResults.isEmpty()) {
                Text(
                    text = "No results",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                LazyColumn {
                    items(viewModel.searchResults) { diary ->
                        ListItem(
                            headlineContent = {
                                val formatted = runCatching {
                                    LocalDate.parse(diary.date).format(resultDateFormatter)
                                }.getOrDefault(diary.date)
                                Text(formatted)
                            },

                            supportingContent = {
                                Text(
                                    diary.content,
                                    maxLines = 2
                                )
                            },
                            modifier = Modifier.clickable {
                                onResultClick(LocalDate.parse(diary.date))
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}
