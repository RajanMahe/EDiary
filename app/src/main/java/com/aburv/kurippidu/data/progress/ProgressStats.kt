package com.aburv.kurippidu.data.progress

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** One entry's workout as shown on the review sheet. */
data class EntryWorkout(
    val sets: List<ParsedSet> = emptyList(),
    val notes: List<String> = emptyList(),
    val confirmed: Boolean = false,
    val changedSinceReview: Boolean = false
)

/** A day's sets; [confirmed] once the user reviewed them. */
data class WorkoutDay(val date: LocalDate, val sets: List<ParsedSet>, val confirmed: Boolean)

/** One exercise on one day, warm-ups left out. */
data class ExerciseSession(
    val date: LocalDate,
    val bestWeightKg: Double?,
    val totalReps: Int,
    val volume: Double,
    val minutes: Double?
)

data class PersonalBest(val exercise: String, val weightKg: Double, val date: LocalDate)

/**
 * Left vs right for one exercise on its latest day with both sides.
 * [unit] is "kg" when both sides had a weight, else "reps".
 */
data class BalanceRow(
    val exercise: String,
    val date: LocalDate,
    val left: Double,
    val right: Double,
    val unit: String,
    /** Gap on each day both sides were done, oldest first. */
    val trend: List<Int>
) {
    val gapPercent: Int get() = trend.last()
    val weaker: Side? get() = when {
        gapPercent < EVEN_PERCENT -> null
        left < right -> Side.LEFT
        else -> Side.RIGHT
    }

    companion object {
        /** Under this gap the sides count as even. */
        const val EVEN_PERCENT = 5
    }
}

object ProgressStats {

    /** "Lunges", "lunge" and "LUNGES " are the same exercise. */
    fun key(name: String): String =
        name.lowercase().replace(Regex("""[^\p{L}\d ]"""), " ").trim().replace(Regex("""\s+"""), " ")
            .split(" ").joinToString(" ") { if (it.length > 3 && it.endsWith("s")) it.dropLast(1) else it }

    private fun working(days: List<WorkoutDay>) =
        days.flatMap { day -> day.sets.filter { !it.warmUp }.map { day.date to it } }

    /** Exercise names: weighted ones first, then most often done; the latest spelling is shown. */
    fun exercises(days: List<WorkoutDay>): List<String> =
        working(days).groupBy { key(it.second.exercise) }
            .entries.sortedWith(
                compareByDescending<Map.Entry<String, List<Pair<LocalDate, ParsedSet>>>> { e -> e.value.any { it.second.weightKg != null } }
                    .thenByDescending { e -> e.value.map { it.first }.distinct().size }
                    .thenBy { it.key }
            )
            .map { e -> e.value.maxBy { it.first }.second.exercise }

    fun sessions(days: List<WorkoutDay>, exercise: String): List<ExerciseSession> {
        val k = key(exercise)
        return working(days).filter { key(it.second.exercise) == k }.groupBy { it.first }
            .map { (date, rows) ->
                val sets = rows.map { it.second }
                ExerciseSession(
                    date,
                    sets.mapNotNull { it.weightKg }.maxOrNull(),
                    sets.sumOf { (it.reps ?: 0) * it.sets },
                    sets.sumOf { it.volume },
                    sets.mapNotNull { it.minutes }.takeIf { it.isNotEmpty() }?.sum()
                )
            }.sortedBy { it.date }
    }

    /** Heaviest weight per exercise, heaviest first. */
    fun personalBests(days: List<WorkoutDay>): List<PersonalBest> =
        working(days).filter { it.second.weightKg != null }.groupBy { key(it.second.exercise) }
            .map { (_, rows) ->
                // First day it was reached
                val best = rows.maxWith(compareBy<Pair<LocalDate, ParsedSet>> { it.second.weightKg!! }.thenByDescending { it.first })
                PersonalBest(best.second.exercise, best.second.weightKg!!, best.first)
            }.sortedByDescending { it.weightKg }

    /** kg × reps × sets per week (weeks start Monday), oldest first. */
    fun weeklyVolume(days: List<WorkoutDay>): List<Pair<LocalDate, Double>> =
        working(days).groupBy { it.first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .map { (week, rows) -> week to rows.sumOf { it.second.volume } }
            .sortedBy { it.first }

    /** Exercises done on both sides, biggest gap first. */
    fun balance(days: List<WorkoutDay>): List<BalanceRow> =
        working(days).filter { it.second.side != Side.BOTH }.groupBy { key(it.second.exercise) }
            .mapNotNull { (_, rows) ->
                val perDay = rows.groupBy { it.first }.mapNotNull { (date, dayRows) ->
                    val left = dayRows.map { it.second }.filter { it.side == Side.LEFT }
                    val right = dayRows.map { it.second }.filter { it.side == Side.RIGHT }
                    if (left.isEmpty() || right.isEmpty()) return@mapNotNull null
                    val byWeight = left.any { it.weightKg != null } && right.any { it.weightKg != null }
                    fun score(sets: List<ParsedSet>) =
                        if (byWeight) sets.mapNotNull { it.weightKg }.max() else sets.maxOf { (it.reps ?: 0).toDouble() }
                    Triple(date, score(left) to score(right), if (byWeight) "kg" else "reps")
                }.sortedBy { it.first }
                if (perDay.isEmpty()) return@mapNotNull null
                val (date, scores, unit) = perDay.last()
                val name = rows.maxBy { it.first }.second.exercise
                BalanceRow(name, date, scores.first, scores.second, unit, perDay.map { gap(it.second.first, it.second.second) })
            }.sortedByDescending { it.gapPercent }

    private fun gap(left: Double, right: Double): Int {
        val top = max(left, right)
        return if (top == 0.0) 0 else (abs(left - right) / top * 100).roundToInt()
    }

    /** Days since the last workout, or null if there is none yet. */
    fun daysSinceLast(days: List<WorkoutDay>, today: LocalDate): Long? =
        days.maxOfOrNull { it.date }?.let { ChronoUnit.DAYS.between(it, today) }

    fun workoutDaysInLast(days: List<WorkoutDay>, today: LocalDate, length: Long): Int =
        days.count { !it.date.isAfter(today) && it.date.isAfter(today.minusDays(length)) }
}

/** Food averages over the days that were written in. Estimates. */
data class FoodSummary(
    val daysLogged: Int,
    val avgKcal: Int,
    val avgProteinG: Int,
    val avgWaterLitres: Double,
    val friedPerWeek: Double,
    val sweetPerWeek: Double,
    /** Food name and how many times, most frequent first. */
    val topFoods: List<Pair<String, Int>>,
    val notInTable: List<String>
) {
    companion object {
        fun of(days: List<Pair<LocalDate, FoodDay>>, today: LocalDate, length: Long = 28): FoodSummary {
            val recent = days.filter { !it.first.isAfter(today) && it.first.isAfter(today.minusDays(length)) }.map { it.second }
            val n = recent.size
            val weeks = length / 7.0
            fun avg(sum: Double) = if (n == 0) 0.0 else sum / n
            return FoodSummary(
                daysLogged = n,
                avgKcal = avg(recent.sumOf { it.kcal.toDouble() }).roundToInt(),
                avgProteinG = avg(recent.sumOf { it.proteinG.toDouble() }).roundToInt(),
                avgWaterLitres = avg(recent.sumOf { it.waterLitres }),
                friedPerWeek = recent.sumOf { it.friedCount } / weeks,
                sweetPerWeek = recent.sumOf { it.sweetCount } / weeks,
                topFoods = recent.flatMap { it.items }.filter { it.food != null && !it.isWater }
                    .groupingBy { it.food!!.name }.eachCount().entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .take(8).map { it.key to it.value },
                notInTable = recent.flatMap { it.unknown }.distinct()
            )
        }
    }
}
