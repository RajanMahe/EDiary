package com.aburv.kurippidu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Pick the language to speak in; picking one starts listening. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechLanguageSheet(selected: SpeechLanguage?, onPick: (SpeechLanguage) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Speak in", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Long-press the mic to change it later. If a language isn't installed, your phone may offer to download it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(SpeechLanguage.entries) { language ->
                ListItem(
                    headlineContent = { Text(language.display) },
                    trailingContent = { if (language == selected) Icon(Icons.Default.Check, contentDescription = "Selected") },
                    modifier = Modifier.clickable { onPick(language) }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
