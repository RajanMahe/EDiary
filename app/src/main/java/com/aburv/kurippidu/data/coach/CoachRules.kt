package com.aburv.kurippidu.data.coach

import com.aburv.kurippidu.data.DiaryType
import com.aburv.kurippidu.data.progress.FoodDay
import com.aburv.kurippidu.data.progress.FoodSummary
import com.aburv.kurippidu.data.progress.ProgressStats
import com.aburv.kurippidu.data.progress.Side
import com.aburv.kurippidu.data.progress.WorkoutDay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** How a tip should feel: well done, a gentle push, or take care. */
enum class TipKind { PRAISE, NUDGE, CARE }

/**
 * One short coaching tip. [id] stays the same while the situation does,
 * so a tip that was already shown in a notification isn't sent twice.
 */
data class CoachTip(
    val id: String,
    val kind: TipKind,
    val emoji: String,
    val title: String,
    val body: String,
    /** Higher shows first. Care before nudges before praise of the same weight. */
    val priority: Int
)

/** What the rules look at for one diary. */
data class CoachInput(
    val type: DiaryType,
    val today: LocalDate,
    val workouts: List<WorkoutDay> = emptyList(),
    val food: List<Pair<LocalDate, FoodDay>> = emptyList(),
    /** Entry text by day, for words like "pain" that the parsers don't count. */
    val entries: List<Pair<LocalDate, String>> = emptyList()
)

/**
 * Coaching tips made from simple rules, no AI. They work on every phone and
 * offline. Wording is supportive and general; anything about pain points
 * to a physio or doctor instead of giving advice.
 */
object CoachRules {

    /** Days without a workout before a nudge. */
    const val REST_NUDGE_DAYS = 4L
    /** Left/right gap worth a tip, in percent. */
    const val BALANCE_GAP = 10
    /** Weekly volume jump worth a care tip. */
    const val VOLUME_JUMP = 1.3

    private val painWords = Regex(
        """\b(pain|painful|hurts?|hurting|swell(ing|ed)?|sharp|clicking|tick(ing)?|sore(ness)?|sournes+|injur(y|ed))\b""",
        RegexOption.IGNORE_CASE
    )

    fun tips(input: CoachInput): List<CoachTip> {
        val tips = mutableListOf<CoachTip>()
        val tracksWorkouts = input.type != DiaryType.FOOD
        val tracksFood = input.type == DiaryType.FOOD || input.type == DiaryType.PLAIN
        if (tracksWorkouts && input.workouts.isNotEmpty()) tips += workoutTips(input)
        if (tracksWorkouts) tips += painTips(input)
        if (tracksFood && input.food.isNotEmpty()) tips += foodTips(input)
        return tips.sortedWith(compareByDescending<CoachTip> { it.priority }.thenByDescending { it.kind.ordinal })
    }

    /* ---------------- WORKOUT ---------------- */

    private fun workoutTips(input: CoachInput): List<CoachTip> {
        val tips = mutableListOf<CoachTip>()
        val days = input.workouts.filter { !it.date.isAfter(input.today) }
        if (days.isEmpty()) return tips
        val today = input.today
        val rehab = input.type == DiaryType.REHAB

        // Missed days, only for someone who trained recently
        val since = ProgressStats.daysSinceLast(days, today) ?: return tips
        if (since >= REST_NUDGE_DAYS && since <= 60) {
            tips += CoachTip(
                "rest-$since", TipKind.NUDGE, "⏰", "No workout for $since days",
                if (rehab) "Even a short session of your easiest exercises keeps the progress going. If something hurts, check with your physio first."
                else "A short session today is enough to get back on track. Start lighter than last time.",
                priority = 60
            )
        }

        val week = ProgressStats.workoutDaysInLast(days, today, 7)
        if (week >= 4) {
            tips += CoachTip(
                "consistent-${today.minusDays(today.dayOfWeek.value - 1L)}", TipKind.PRAISE, "🔥",
                "$week workout days this week",
                "Great consistency. Keep at least one easy or rest day so your body can recover.",
                priority = 30
            )
        }

        // Left / right balance
        ProgressStats.balance(days).forEach { row ->
            val weaker = row.weaker ?: return@forEach
            val weakName = if (weaker == Side.LEFT) "Left" else "Right"
            val strongName = if (weaker == Side.LEFT) "right" else "left"
            val start = row.trend.first()
            when {
                row.trend.size > 1 && start - row.gapPercent >= 5 -> tips += CoachTip(
                    "balance-closing-${ProgressStats.key(row.exercise)}-${row.gapPercent}", TipKind.PRAISE, "📈",
                    "${row.exercise}: gap closing",
                    "$weakName side was $start% behind, now ${row.gapPercent}%. Keep giving it the extra work.",
                    priority = 40
                )
                row.gapPercent >= BALANCE_GAP && ChronoUnit.DAYS.between(row.date, today) <= 30 -> tips += CoachTip(
                    "balance-${ProgressStats.key(row.exercise)}-${row.gapPercent}", TipKind.NUDGE, "⚖️",
                    "${row.exercise}: $weakName ${row.gapPercent}% behind",
                    "Start with the $weakName side and match its reps on the $strongName side. " +
                        if (rehab) "Ask your physio if an extra $weakName-only set suits your plan." else "An extra $weakName-only set helps it catch up.",
                    priority = 50 + row.gapPercent.coerceAtMost(30)
                )
            }
        }

        // New personal bests this week
        ProgressStats.personalBests(days).filter { ChronoUnit.DAYS.between(it.date, today) in 0..6 }.take(2).forEach { best ->
            val earlier = days.filter { it.date.isBefore(best.date) }
            // A "best" on the very first session isn't news
            if (ProgressStats.sessions(earlier, best.exercise).isEmpty()) return@forEach
            tips += CoachTip(
                "pb-${ProgressStats.key(best.exercise)}-${best.weightKg}", TipKind.PRAISE, "🏆",
                "New best: ${best.exercise} ${fmt(best.weightKg)} kg",
                "Your heaviest yet. Keep the form clean before going heavier.",
                priority = 45
            )
        }

        // Same weight for weeks
        ProgressStats.exercises(days).forEach { exercise ->
            val sessions = ProgressStats.sessions(days, exercise).filter { it.bestWeightKg != null }
            val recent = sessions.filter { ChronoUnit.DAYS.between(it.date, today) <= 21 }
            if (recent.size < 3) return@forEach
            val span = ChronoUnit.DAYS.between(recent.first().date, recent.last().date)
            val weights = recent.map { it.bestWeightKg!! }.distinct()
            if (span >= 14 && weights.size == 1 && ChronoUnit.DAYS.between(recent.last().date, today) <= 7) {
                tips += CoachTip(
                    "plateau-${ProgressStats.key(exercise)}-${weights.single()}", TipKind.NUDGE, "🧗",
                    "$exercise: ${fmt(weights.single())} kg for ${span / 7 + 1} weeks",
                    if (rehab) "If the last sessions felt easy and pain-free, ask your physio about the next step up."
                    else "If the last sessions felt easy, try +2.5 kg, or 2 more reps at the same weight.",
                    priority = 35
                )
            }
        }

        // Big jump in volume
        val weekly = ProgressStats.weeklyVolume(days)
        if (weekly.size >= 2) {
            val (thisWeek, now) = weekly.last()
            val (_, before) = weekly[weekly.size - 2]
            if (ChronoUnit.DAYS.between(thisWeek, today) in 0..6 && before > 0 && now > before * VOLUME_JUMP) {
                val up = ((now / before - 1) * 100).roundToInt()
                tips += CoachTip(
                    "volume-$thisWeek", TipKind.CARE, "🛑", "Volume up $up% this week",
                    "Big jumps raise the risk of injury. Adding about 10% a week is safer" +
                        if (rehab) ", especially during rehab." else ".",
                    priority = 70
                )
            }
        }

        val unreviewed = days.count { !it.confirmed && ChronoUnit.DAYS.between(it.date, today) <= 28 }
        if (unreviewed >= 5) {
            tips += CoachTip(
                "review-$unreviewed", TipKind.NUDGE, "✅", "$unreviewed workouts not reviewed",
                "Tap \"Review workout\" on those days so the numbers here are right.",
                priority = 10
            )
        }
        return tips
    }

    private fun painTips(input: CoachInput): List<CoachTip> {
        val recent = input.entries.filter { (date, _) -> ChronoUnit.DAYS.between(date, input.today) in 0..6 }
        val painDays = recent.filter { painWords.containsMatchIn(it.second) }.map { it.first }.distinct()
        if (painDays.isEmpty()) return emptyList()
        return listOf(
            CoachTip(
                "pain-${painDays.max()}", TipKind.CARE, "🩺",
                if (painDays.size == 1) "You mentioned pain or soreness" else "Pain or soreness on ${painDays.size} days",
                "Mild soreness after training is common, but pain that's sharp, swelling, or lasts more than 2 days needs a check by your physio or doctor before you train it again.",
                priority = 90
            )
        )
    }

    /* ---------------- FOOD ---------------- */

    private fun foodTips(input: CoachInput): List<CoachTip> {
        val tips = mutableListOf<CoachTip>()
        val week = FoodSummary.of(input.food, input.today, length = 7)
        if (week.daysLogged < 3) return tips
        val weekStart = input.today.minusDays(input.today.dayOfWeek.value - 1L)
        val fried = week.friedPerWeek.roundToInt()
        val sweet = week.sweetPerWeek.roundToInt()
        val loggedWater = input.food.any { (date, day) ->
            ChronoUnit.DAYS.between(date, input.today) in 0..6 && day.waterLitres > 0
        }

        if (fried >= 4) tips += CoachTip(
            "fried-$weekStart", TipKind.NUDGE, "🍳", "$fried fried items this week",
            "Try swapping a few for steamed or roasted options, like idli instead of vada, or sundal instead of chips.",
            priority = 40
        )
        if (sweet >= 5) tips += CoachTip(
            "sweet-$weekStart", TipKind.NUDGE, "🍬", "$sweet sweet items this week",
            "Fruit, tender coconut or buttermilk can stand in for some of them.",
            priority = 35
        )
        if (loggedWater && week.avgWaterLitres < 2.0) tips += CoachTip(
            "water-$weekStart", TipKind.NUDGE, "💧", "About ${fmt(week.avgWaterLitres)} L water a day",
            "Most adults need around 2–3 L a day, more on training days or in hot weather.",
            priority = 45
        )
        if (!loggedWater) tips += CoachTip(
            "water-log-$weekStart", TipKind.NUDGE, "💧", "No water logged this week",
            "Add a line like \"2 L water\" to see if you're drinking enough.",
            priority = 15
        )
        if (week.avgProteinG in 1..44) tips += CoachTip(
            "protein-$weekStart", TipKind.NUDGE, "🥚", "Protein about ${week.avgProteinG} g a day",
            "Many adults need roughly 50–60 g. Dal, eggs, curd, paneer, sundal or chicken are easy ways to add it.",
            priority = 30
        )
        if (week.daysLogged >= 6) tips += CoachTip(
            "food-logged-$weekStart", TipKind.PRAISE, "📝", "Food logged ${week.daysLogged} days this week",
            "Keeping track is the hardest part. Well done.",
            priority = 20
        )
        return tips
    }

    private fun fmt(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
}
