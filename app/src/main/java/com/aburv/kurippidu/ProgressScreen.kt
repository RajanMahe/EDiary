package com.aburv.kurippidu

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import com.aburv.kurippidu.data.coach.CoachInput
import com.aburv.kurippidu.data.coach.CoachRules
import com.aburv.kurippidu.data.coach.CoachTip
import com.aburv.kurippidu.data.coach.TipKind
import com.aburv.kurippidu.data.diaryType
import com.aburv.kurippidu.data.progress.BalanceRow
import com.aburv.kurippidu.data.progress.ExerciseSession
import com.aburv.kurippidu.data.progress.PersonalBest
import com.aburv.kurippidu.data.progress.FoodDay
import com.aburv.kurippidu.data.progress.FoodSummary
import com.aburv.kurippidu.data.progress.ProgressStats
import com.aburv.kurippidu.data.progress.Side
import com.aburv.kurippidu.data.progress.WorkoutDay
import com.aburv.kurippidu.ui.charts.BarChart
import com.aburv.kurippidu.ui.charts.ChartPoint
import com.aburv.kurippidu.ui.charts.LineChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Everything the Workout tab shows, worked out once in the background. */
data class WorkoutView(
    val days: List<WorkoutDay>,
    val exercises: List<String>,
    val sessions: Map<String, List<ExerciseSession>>,
    val balance: List<BalanceRow>,
    val bests: List<PersonalBest>,
    val weekly: List<Pair<LocalDate, Double>>,
    val daysThisWeek: Int,
    val daysIn4Weeks: Int,
    val daysSinceLast: Long?,
    val unreviewed: Int
) {
    companion object {
        fun of(days: List<WorkoutDay>, today: LocalDate): WorkoutView {
            val exercises = ProgressStats.exercises(days)
            return WorkoutView(
                days, exercises,
                exercises.associateWith { ProgressStats.sessions(days, it) },
                ProgressStats.balance(days),
                ProgressStats.personalBests(days).take(6),
                ProgressStats.weeklyVolume(days).takeLast(8),
                ProgressStats.workoutDaysInLast(days, today, 7),
                ProgressStats.workoutDaysInLast(days, today, 28),
                ProgressStats.daysSinceLast(days, today),
                days.count { !it.confirmed }
            )
        }
    }
}

/** Everything the Food tab shows. */
data class FoodView(val recent: List<Pair<LocalDate, FoodDay>>, val summary: FoodSummary, val isEmpty: Boolean)

class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DiaryRepository.get(application)

    var title by mutableStateOf("")
        private set
    var type by mutableStateOf(DiaryType.PLAIN)
        private set
    var workouts by mutableStateOf<WorkoutView?>(null)
        private set
    var food by mutableStateOf<FoodView?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var tips by mutableStateOf<List<CoachTip>>(emptyList())
        private set

    fun load(diaryId: Int, today: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            repository.getDiary(diaryId)?.let {
                title = it.title
                type = it.diaryType
            }
            // Only read what this kind of diary shows
            val workoutDays = if (type != DiaryType.FOOD) repository.workoutHistory(diaryId) else emptyList()
            val foodDays = if (type == DiaryType.FOOD || type == DiaryType.PLAIN) repository.foodHistory(diaryId) else emptyList()
            val entries = repository.getAllEntries(diaryId)
            // Charts, balance and tips are worked out once, in the background
            val (workoutView, foodView, coachTips) = withContext(Dispatchers.Default) {
                val dated = entries.mapNotNull { entry ->
                    runCatching { LocalDate.parse(entry.date) }.getOrNull()?.let { it to entry.content }
                }
                Triple(
                    WorkoutView.of(workoutDays, today),
                    FoodView(foodDays.takeLast(14), FoodSummary.of(foodDays, today), foodDays.isEmpty()),
                    CoachRules.tips(CoachInput(type, today, workoutDays, foodDays, dated))
                )
            }
            workouts = workoutView
            food = foodView
            tips = coachTips
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
    LaunchedEffect(diaryId) { viewModel.load(diaryId, today) }

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
                tabs[tab.coerceAtMost(tabs.lastIndex)] == "Workout" -> viewModel.workouts?.let { WorkoutProgress(it, viewModel.tips) }
                else -> viewModel.food?.let { FoodProgress(it, viewModel.tips) }
            }
        }
    }
}

/* ---------------- WORKOUT ---------------- */

@Composable
private fun WorkoutProgress(view: WorkoutView, tips: List<CoachTip>) {
    if (view.days.isEmpty()) {
        EmptyState("No workouts yet", "Write your sets in the diary, like \"Squat 20 kg 10 rep 3 set\" or \"12.5 - 15\". They show up here.")
        return
    }
    val exercises = view.exercises
    var selected by remember(exercises) { mutableStateOf(exercises.firstOrNull()) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tipItems(tips)
        item { WorkoutSummary(view) }

        if (exercises.isNotEmpty()) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    exercises.forEach { name ->
                        FilterChip(selected = name == selected, onClick = { selected = name }, label = { Text(name) })
                    }
                }
            }
            selected?.let { name -> item { ExerciseCard(view.sessions[name].orEmpty(), name) } }
        }

        val balance = view.balance
        if (balance.isNotEmpty()) {
            item { SectionTitle("Left / right balance") }
            balance.forEach { row -> item { BalanceCard(row) } }
        }

        val bests = view.bests
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

        val weekly = view.weekly
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
private fun WorkoutSummary(view: WorkoutView) {
    val week = view.daysThisWeek
    val month = view.daysIn4Weeks
    val since = view.daysSinceLast
    val unreviewed = view.unreviewed
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Stat("$week", if (week == 1) "day this week" else "days this week", Modifier.weight(1f))
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
private fun ExerciseCard(sessions: List<ExerciseSession>, exercise: String) {
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
private fun FoodProgress(view: FoodView, tips: List<CoachTip>) {
    if (view.isEmpty) {
        EmptyState("No food logged yet", "Write what you ate, like \"2 idli, sambar\" or \"250 ml tender coconut\". It shows up here.")
        return
    }
    val summary = view.summary
    val recent = view.recent

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tipItems(tips)
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

/* ---------------- COACH TIPS ---------------- */

private const val TIPS_SHOWN = 4

/** The coach's tips at the top of the list: the most important few, the rest behind "Show all". */
private fun LazyListScope.tipItems(tips: List<CoachTip>) {
    if (tips.isEmpty()) return
    item { SectionTitle("Coach tips") }
    item {
        var showAll by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (if (showAll) tips else tips.take(TIPS_SHOWN)).forEach { TipCard(it) }
            if (tips.size > TIPS_SHOWN) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Show fewer" else "Show all ${tips.size} tips")
                }
            }
            Text(
                "Tips come from simple rules on what you wrote. They're general guidance, not medical advice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TipCard(tip: CoachTip) {
    val colors = when (tip.kind) {
        TipKind.CARE -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        TipKind.NUDGE -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        TipKind.PRAISE -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    }
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tip.emoji, style = MaterialTheme.typography.titleLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tip.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(tip.body, style = MaterialTheme.typography.bodyMedium)
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
