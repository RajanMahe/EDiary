package com.aburv.kurippidu

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aburv.kurippidu.data.DiaryRepository
import com.aburv.kurippidu.data.DiaryType
import com.aburv.kurippidu.data.diaryType
import com.aburv.kurippidu.data.progress.BalanceRow
import com.aburv.kurippidu.data.progress.FoodDay
import com.aburv.kurippidu.data.progress.FoodSummary
import com.aburv.kurippidu.data.progress.ProgressStats
import com.aburv.kurippidu.data.progress.Side
import com.aburv.kurippidu.data.progress.WorkoutDay
import com.aburv.kurippidu.ui.charts.BarChart
import com.aburv.kurippidu.ui.charts.ChartPoint
import com.aburv.kurippidu.ui.charts.LineChart
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DiaryRepository.get(application)

    var title by mutableStateOf("")
        private set
    var type by mutableStateOf(DiaryType.PLAIN)
        private set
    var workouts by mutableStateOf<List<WorkoutDay>>(emptyList())
        private set
    var food by mutableStateOf<List<Pair<LocalDate, FoodDay>>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set

    fun load(diaryId: Int) {
        viewModelScope.launch {
            repository.getDiary(diaryId)?.let {
                title = it.title
                type = it.diaryType
            }
            workouts = repository.workoutHistory(diaryId)
            food = repository.foodHistory(diaryId)
            isLoading = false
        }
    }
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM")

private fun fmt(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    diaryId: Int,
    onBack: () -> Unit,
    today: LocalDate = LocalDate.now(),
    viewModel: ProgressViewModel = viewModel()
) {
    LaunchedEffect(diaryId) { viewModel.load(diaryId) }

    // Plain diaries can hold either kind, so they get both tabs
    val tabs = when (viewModel.type) {
        DiaryType.GYM, DiaryType.REHAB -> listOf("Workout")
        DiaryType.FOOD -> listOf("Food")
        DiaryType.PLAIN -> listOf("Workout", "Food")
    }
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.title.isEmpty()) "Progress" else "Progress · ${viewModel.title}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (tabs.size > 1) {
                TabRow(selectedTabIndex = tab.coerceAtMost(tabs.lastIndex)) {
                    tabs.forEachIndexed { i, name -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(name) }) }
                }
            }
            when {
                viewModel.isLoading -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.padding(32.dp)) }
                tabs[tab.coerceAtMost(tabs.lastIndex)] == "Workout" -> WorkoutProgress(viewModel.workouts, today)
                else -> FoodProgress(viewModel.food, today)
            }
        }
    }
}

/* ---------------- WORKOUT ---------------- */

@Composable
private fun WorkoutProgress(days: List<WorkoutDay>, today: LocalDate) {
    if (days.isEmpty()) {
        EmptyState("No workouts yet", "Write your sets in the diary, like \"Squat 20 kg 10 rep 3 set\" or \"12.5 - 15\". They show up here.")
        return
    }
    val exercises = remember(days) { ProgressStats.exercises(days) }
    var selected by remember(exercises) { mutableStateOf(exercises.firstOrNull()) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { WorkoutSummary(days, today) }

        if (exercises.isNotEmpty()) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    exercises.forEach { name ->
                        FilterChip(selected = name == selected, onClick = { selected = name }, label = { Text(name) })
                    }
                }
            }
            selected?.let { name -> item { ExerciseCard(days, name) } }
        }

        val balance = ProgressStats.balance(days)
        if (balance.isNotEmpty()) {
            item { SectionTitle("Left / right balance") }
            balance.forEach { row -> item { BalanceCard(row) } }
        }

        val bests = ProgressStats.personalBests(days).take(6)
        if (bests.isNotEmpty()) {
            item { SectionTitle("Personal bests") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        bests.forEach { best ->
                            Row {
                                Text("🏆 ${best.exercise}", Modifier.weight(1f))
                                Text("${fmt(best.weightKg)} kg · ${best.date.format(shortDate)}", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        val weekly = ProgressStats.weeklyVolume(days).takeLast(8)
        if (weekly.any { it.second > 0 }) {
            item { SectionTitle("Weekly volume (kg × reps × sets)") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    BarChart(
                        weekly.map { ChartPoint(it.first.format(shortDate), it.second) },
                        description = "Weekly volume: " + weekly.joinToString { "${it.first.format(shortDate)} ${it.second.toInt()} kg" },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutSummary(days: List<WorkoutDay>, today: LocalDate) {
    val week = ProgressStats.workoutDaysInLast(days, today, 7)
    val month = ProgressStats.workoutDaysInLast(days, today, 28)
    val since = ProgressStats.daysSinceLast(days, today)
    val unreviewed = days.count { !it.confirmed }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Stat("$week", "days this week", Modifier.weight(1f))
                Stat("$month", "in 4 weeks", Modifier.weight(1f))
                Stat(
                    when (since) { null -> "–"; 0L -> "Today"; 1L -> "1 day"; else -> "$since days" },
                    "since last", Modifier.weight(1f)
                )
            }
            if (unreviewed > 0) {
                Text(
                    "$unreviewed ${if (unreviewed == 1) "day is" else "days are"} not reviewed yet. Open the day and tap \"Review workout\" to check the numbers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExerciseCard(days: List<WorkoutDay>, exercise: String) {
    val sessions = ProgressStats.sessions(days, exercise)
    val byWeight = sessions.any { it.bestWeightKg != null }
    val byMinutes = !byWeight && sessions.any { it.minutes != null }
    val points = sessions.mapNotNull { s ->
        val value = when {
            byWeight -> s.bestWeightKg
            byMinutes -> s.minutes
            else -> s.totalReps.toDouble().takeIf { it > 0 }
        } ?: return@mapNotNull null
        ChartPoint(s.date.format(shortDate), value)
    }
    val unit = when { byWeight -> " kg"; byMinutes -> " min"; else -> " reps" }
    val what = when { byWeight -> "Heaviest weight"; byMinutes -> "Minutes"; else -> "Total reps" }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$what · $exercise", style = MaterialTheme.typography.titleMedium)
            if (points.isEmpty()) {
                Text("No numbers for this one yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                LineChart(
                    points, unit = unit,
                    description = "$what for $exercise: " + points.joinToString { "${it.label} ${fmt(it.value)}$unit" }
                )
                val first = points.first().value
                val last = points.last().value
                if (points.size > 1 && first > 0) {
                    val change = ((last - first) / first * 100).toInt()
                    Text(
                        when {
                            change > 0 -> "Up $change% since ${points.first().label}"
                            change < 0 -> "Down ${-change}% since ${points.first().label}"
                            else -> "Same as ${points.first().label}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(row: BalanceRow) {
    val weaker = row.weaker
    val color = when {
        weaker == null -> MaterialTheme.colorScheme.primary
        row.gapPercent >= 15 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(row.exercise, style = MaterialTheme.typography.titleSmall)
            Row {
                Text("Left ${fmt(row.left)} ${row.unit}", Modifier.weight(1f))
                Text("Right ${fmt(row.right)} ${row.unit}")
            }
            LinearProgressIndicator(
                progress = { (100 - row.gapPercent).coerceIn(0, 100) / 100f },
                color = color,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                when (weaker) {
                    null -> "Even (${row.date.format(shortDate)})"
                    Side.LEFT -> "Left is ${row.gapPercent}% behind (${row.date.format(shortDate)})"
                    else -> "Right is ${row.gapPercent}% behind (${row.date.format(shortDate)})"
                },
                color = color,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            if (row.trend.size > 1) {
                val first = row.trend.first()
                Text(
                    when {
                        row.gapPercent < first -> "Gap was $first% at the start: closing ✓"
                        row.gapPercent > first -> "Gap was $first% at the start: growing"
                        else -> "Gap unchanged over ${row.trend.size} sessions"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ---------------- FOOD ---------------- */

@Composable
private fun FoodProgress(days: List<Pair<LocalDate, FoodDay>>, today: LocalDate) {
    if (days.isEmpty()) {
        EmptyState("No food logged yet", "Write what you ate, like \"2 idli, sambar\" or \"250 ml tender coconut\". It shows up here.")
        return
    }
    val summary = remember(days, today) { FoodSummary.of(days, today) }
    val recent = days.takeLast(14)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Estimates from typical Indian food values, not exact counts and not medical advice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Last 4 weeks · ${summary.daysLogged} days logged", style = MaterialTheme.typography.labelLarge)
                    Row {
                        Stat("~${summary.avgKcal}", "kcal a day", Modifier.weight(1f))
                        Stat("~${summary.avgProteinG} g", "protein a day", Modifier.weight(1f))
                        Stat("${fmt(summary.avgWaterLitres)} L", "water a day", Modifier.weight(1f))
                    }
                    Row {
                        Stat(fmt(summary.friedPerWeek), "fried items a week", Modifier.weight(1f))
                        Stat(fmt(summary.sweetPerWeek), "sweets a week", Modifier.weight(1f))
                    }
                }
            }
        }
        item { SectionTitle("Calories by day") }
        item {
            Card(Modifier.fillMaxWidth()) {
                BarChart(
                    recent.map { ChartPoint(it.first.format(shortDate), it.second.kcal.toDouble()) },
                    description = "Calories by day: " + recent.joinToString { "${it.first.format(shortDate)} ${it.second.kcal}" },
                    modifier = Modifier.padding(16.dp),
                    unit = " kcal"
                )
            }
        }
        if (summary.topFoods.isNotEmpty()) {
            item { SectionTitle("Most eaten") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        summary.topFoods.forEach { (name, count) ->
                            Row {
                                Text(name, Modifier.weight(1f))
                                Text("${count}×", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        if (summary.notInTable.isNotEmpty()) {
            item {
                Text(
                    "Not in the food list yet, so not counted: " + summary.notInTable.take(10).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ---------------- SHARED ---------------- */

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("📈", style = MaterialTheme.typography.displaySmall)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
