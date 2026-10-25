package com.aburv.kurippidu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aburv.kurippidu.data.progress.EntryWorkout
import com.aburv.kurippidu.data.progress.ParsedSet
import com.aburv.kurippidu.data.progress.Side

/** The chip under the word count: how far this day's workout has been reviewed. */
@Composable
fun WorkoutStatusChip(workout: EntryWorkout, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (workout.sets.isEmpty() && !workout.confirmed) return
    val count = workout.sets.size
    val label = when {
        workout.confirmed && workout.changedSinceReview -> "Entry changed – review workout again"
        workout.confirmed -> "✓ Workout saved ($count)"
        else -> "Review workout ($count)"
    }
    AssistChip(onClick = onClick, label = { Text(label) }, modifier = modifier)
}

/** One editable row; numbers are kept as text while typing. */
private class SetDraft(set: ParsedSet) {
    var exercise by mutableStateOf(set.exercise)
    var side by mutableStateOf(set.side)
    var kg by mutableStateOf(set.weightKg?.let(::formatNumber).orEmpty())
    var reps by mutableStateOf(set.reps?.toString().orEmpty())
    var sets by mutableStateOf(set.sets.toString())
    var minutes by mutableStateOf(set.minutes?.let(::formatNumber).orEmpty())
    val warmUp = set.warmUp

    fun toSet() = ParsedSet(
        exercise = exercise.trim().ifEmpty { "Exercise" },
        side = side,
        weightKg = kg.replace(',', '.').toDoubleOrNull(),
        reps = reps.toIntOrNull(),
        sets = sets.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        minutes = minutes.replace(',', '.').toDoubleOrNull(),
        warmUp = warmUp
    )
}

private fun formatNumber(value: Double) =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/**
 * Lists what was read from the entry so the user can fix it before it counts
 * towards Progress. Nothing is saved until Confirm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutReviewSheet(
    workout: EntryWorkout,
    onConfirm: (List<ParsedSet>) -> Unit,
    onDismiss: () -> Unit
) {
    val drafts = remember(workout) { mutableStateListOf(*workout.sets.map(::SetDraft).toTypedArray()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Review workout", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Check what was read from your entry. Fix anything wrong, then confirm. Only confirmed workouts count fully in Progress.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            itemsIndexed(drafts, key = { _, draft -> System.identityHashCode(draft) }) { index, draft ->
                SetRow(draft, onRemove = { drafts.removeAt(index) })
            }
            item {
                TextButton(onClick = { drafts += SetDraft(ParsedSet(drafts.lastOrNull()?.exercise ?: "")) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add a set")
                }
            }
            if (workout.notes.isNotEmpty()) {
                item {
                    Text("Not counted (kept in your entry)", style = MaterialTheme.typography.labelLarge)
                    workout.notes.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(drafts.map { it.toSet() }) }) { Text("Confirm") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetRow(draft: SetDraft, onRemove: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft.exercise,
                    onValueChange = { draft.exercise = it },
                    label = { Text("Exercise") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Remove set") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                listOf(Side.LEFT to "Left", Side.BOTH to "Both", Side.RIGHT to "Right").forEach { (side, label) ->
                    FilterChip(selected = draft.side == side, onClick = { draft.side = side }, label = { Text(label) })
                }
                if (draft.warmUp) {
                    Text(
                        "Warm-up",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("kg", draft.kg, decimal = true, Modifier.weight(1f)) { draft.kg = it }
                NumberField("Reps", draft.reps, decimal = false, Modifier.weight(1f)) { draft.reps = it }
                NumberField("Sets", draft.sets, decimal = false, Modifier.weight(1f)) { draft.sets = it }
                if (draft.minutes.isNotEmpty()) {
                    NumberField("Min", draft.minutes, decimal = true, Modifier.weight(1f)) { draft.minutes = it }
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, decimal: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> if (text.all { it.isDigit() || (decimal && (it == '.' || it == ',')) }) onChange(text) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier
    )
}
