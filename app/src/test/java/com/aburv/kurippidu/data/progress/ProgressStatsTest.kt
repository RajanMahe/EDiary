package com.aburv.kurippidu.data.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ProgressStatsTest {

    // 2026-01-05 is a Monday
    private val monday = LocalDate.of(2026, 1, 5)

    private fun day(offset: Long, vararg sets: ParsedSet, confirmed: Boolean = true) =
        WorkoutDay(monday.plusDays(offset), sets.toList(), confirmed)

    private fun set(name: String, kg: Double? = null, reps: Int? = null, sets: Int = 1, side: Side = Side.BOTH, warmUp: Boolean = false) =
        ParsedSet(name, side, kg, reps, sets, warmUp = warmUp)

    @Test
    fun `spellings of one exercise are grouped`() {
        assertEquals(ProgressStats.key("Lunges"), ProgressStats.key("lunge"))
        assertEquals(ProgressStats.key("Leg Press - single"), ProgressStats.key("leg press single"))
    }

    @Test
    fun `exercises with weights come first, then the most often done`() {
        val days = listOf(
            day(0, set("Walk"), set("Squat", 20.0, 10)),
            day(1, set("Walk"), set("Plank", reps = 3)),
            day(2, set("Walk"), set("Plank", reps = 3))
        )
        assertEquals(listOf("Squat", "Walk", "Plank"), ProgressStats.exercises(days))
    }

    @Test
    fun `sessions leave warm-ups out`() {
        val days = listOf(
            day(0, set("Squat", 10.0, 8, warmUp = true), set("Squat", 20.0, 10, sets = 3)),
            day(2, set("squat", 25.0, 8, sets = 2))
        )
        val sessions = ProgressStats.sessions(days, "Squat")
        assertEquals(listOf(20.0, 25.0), sessions.map { it.bestWeightKg })
        assertEquals(listOf(600.0, 400.0), sessions.map { it.volume })
        assertEquals(listOf(30, 16), sessions.map { it.totalReps })
    }

    @Test
    fun `personal best is the first day the heaviest weight was lifted`() {
        val days = listOf(
            day(0, set("Squat", 30.0, 5)),
            day(3, set("Squat", 30.0, 5)),
            day(1, set("Curl", 10.0, 10))
        )
        val bests = ProgressStats.personalBests(days)
        assertEquals(listOf("Squat" to monday, "Curl" to monday.plusDays(1)), bests.map { it.exercise to it.date })
    }

    @Test
    fun `weekly volume starts on Monday`() {
        val days = listOf(
            day(0, set("Squat", 10.0, 10)),       // Mon
            day(6, set("Squat", 10.0, 10)),       // Sun, same week
            day(7, set("Squat", 20.0, 10, sets = 2)) // next Mon
        )
        assertEquals(listOf(monday to 200.0, monday.plusDays(7) to 400.0), ProgressStats.weeklyVolume(days))
    }

    @Test
    fun `left and right balance uses weight when both sides have it, else reps`() {
        val days = listOf(
            day(0, set("Quad", 10.0, 15, side = Side.LEFT), set("Quad", 12.5, 15, side = Side.RIGHT)),
            day(4, set("Quad", 12.5, 15, side = Side.LEFT), set("Quad", 12.5, 15, side = Side.RIGHT)),
            day(1, set("Calf rise", reps = 25, side = Side.LEFT), set("Calf rise", reps = 32, side = Side.RIGHT)),
            day(2, set("Lunges", 10.0, 12, side = Side.LEFT)) // one side only: no balance
        )
        val rows = ProgressStats.balance(days)
        assertEquals(listOf("Calf rise", "Quad"), rows.map { it.exercise })

        val calf = rows[0]
        assertEquals("reps", calf.unit)
        assertEquals(22, calf.gapPercent)
        assertEquals(Side.LEFT, calf.weaker)

        val quad = rows[1]
        assertEquals("kg", quad.unit)
        assertEquals(listOf(20, 0), quad.trend)
        assertNull(quad.weaker) // even now
    }

    @Test
    fun `days since last and days in a window`() {
        val days = listOf(day(0, set("Squat", 10.0, 10)), day(5, set("Squat", 10.0, 10)))
        val today = monday.plusDays(8)
        assertEquals(3L, ProgressStats.daysSinceLast(days, today))
        assertEquals(1, ProgressStats.workoutDaysInLast(days, today, 7))
        assertEquals(2, ProgressStats.workoutDaysInLast(days, today, 28))
        assertNull(ProgressStats.daysSinceLast(emptyList(), today))
    }

    @Test
    fun `food summary averages logged days`() {
        val days = listOf(
            monday to FoodParser.parseDay("3 idli, sambar\n2L water\n2 vada"),
            monday.plusDays(1) to FoodParser.parseDay("1 L water\n2 idli\n1 gulab jamun")
        )
        val summary = FoodSummary.of(days, today = monday.plusDays(2))
        assertEquals(2, summary.daysLogged)
        assertEquals(1.5, summary.avgWaterLitres, 0.001)
        assertEquals(listOf("Idli" to 2), summary.topFoods.take(1))
        assertEquals(0.5, summary.friedPerWeek, 0.001) // vada + jamun over 4 weeks = 2 / 4
        assertEquals(0.25, summary.sweetPerWeek, 0.001)
    }
}
