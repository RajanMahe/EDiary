package com.aburv.kurippidu.data.coach

import com.aburv.kurippidu.data.DiaryType
import com.aburv.kurippidu.data.progress.FoodParser
import com.aburv.kurippidu.data.progress.ParsedSet
import com.aburv.kurippidu.data.progress.Side
import com.aburv.kurippidu.data.progress.WorkoutDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoachRulesTest {

    // A Sunday, so "this week" is Mon 2 Feb … Sun 8 Feb
    private val today = LocalDate.of(2026, 2, 8)

    private fun day(daysAgo: Long, vararg sets: ParsedSet, confirmed: Boolean = true) =
        WorkoutDay(today.minusDays(daysAgo), sets.toList(), confirmed)

    private fun set(name: String, kg: Double? = null, reps: Int? = 10, sets: Int = 1, side: Side = Side.BOTH) =
        ParsedSet(name, side, kg, reps, sets)

    private fun tips(type: DiaryType = DiaryType.GYM, workouts: List<WorkoutDay> = emptyList(), food: String? = null, entries: Map<Long, String> = emptyMap()) =
        CoachRules.tips(
            CoachInput(
                type, today, workouts,
                food = food?.split("\n---\n")?.mapIndexed { i, text -> today.minusDays(i.toLong()) to FoodParser.parseDay(text) }.orEmpty(),
                entries = entries.map { (ago, text) -> today.minusDays(ago) to text }
            )
        )

    private fun titles(list: List<CoachTip>) = list.map { it.title }

    @Test
    fun `nothing to say about an empty diary`() {
        assertEquals(emptyList<CoachTip>(), tips())
        assertEquals(emptyList<CoachTip>(), tips(DiaryType.FOOD))
    }

    @Test
    fun `a gap of days gets a nudge, a long break does not`() {
        assertEquals(listOf("No workout for 6 days"), titles(tips(workouts = listOf(day(6, set("Squat", 20.0))))))
        assertEquals(emptyList<String>(), titles(tips(workouts = listOf(day(2, set("Squat", 20.0))))))
        assertEquals(emptyList<String>(), titles(tips(workouts = listOf(day(90, set("Squat", 20.0))))))
    }

    @Test
    fun `rehab wording sends questions to the physio`() {
        val tip = tips(DiaryType.REHAB, listOf(day(5, set("SLR", 1.0)))).single()
        assertTrue(tip.body.contains("physio"))
    }

    @Test
    fun `four days this week is praised`() {
        val week = (0L..3L).map { day(it, set("Walk", reps = null).copy(minutes = 10.0)) }
        val tip = tips(workouts = week).single()
        assertEquals(TipKind.PRAISE, tip.kind)
        assertEquals("4 workout days this week", tip.title)
    }

    @Test
    fun `weaker side gets a nudge, a closing gap gets praise`() {
        val lagging = tips(
            workouts = listOf(day(1, set("Calf rise", reps = 20, side = Side.LEFT), set("Calf rise", reps = 26, side = Side.RIGHT)))
        )
        assertEquals("Calf rise: Left 23% behind", lagging.first().title)
        assertTrue(lagging.first().body.startsWith("Start with the Left side"))

        val closing = tips(
            workouts = listOf(
                day(9, set("Calf rise", reps = 20, side = Side.LEFT), set("Calf rise", reps = 26, side = Side.RIGHT)),
                day(1, set("Calf rise", reps = 24, side = Side.LEFT), set("Calf rise", reps = 26, side = Side.RIGHT))
            )
        )
        assertEquals("Calf rise: gap closing", closing.first().title)
        assertEquals(TipKind.PRAISE, closing.first().kind)
    }

    @Test
    fun `new best is praised, but not on the first session`() {
        assertTrue(titles(tips(workouts = listOf(day(2, set("Squat", 30.0))))).none { it.startsWith("New best") })
        val tipsNow = tips(workouts = listOf(day(9, set("Squat", 25.0)), day(2, set("Squat", 30.0))))
        assertTrue("New best: Squat 30 kg" in titles(tipsNow))
    }

    @Test
    fun `same weight for weeks suggests a small step up`() {
        val days = listOf(day(20, set("Squat", 20.0)), day(13, set("Squat", 20.0)), day(6, set("Squat", 20.0)), day(1, set("Squat", 20.0)))
        val tip = tips(workouts = days).first { it.title.startsWith("Squat") }
        assertEquals("Squat: 20 kg for 3 weeks", tip.title)
        assertTrue(tip.body.contains("+2.5 kg"))
    }

    @Test
    fun `a big jump in volume comes first as a care tip`() {
        val days = listOf(day(8, set("Squat", 20.0, 10, 3)), day(1, set("Squat", 20.0, 10, 3)), day(0, set("Squat", 20.0, 10, 3)))
        val first = tips(workouts = days).first()
        assertEquals(TipKind.CARE, first.kind)
        assertEquals("Volume up 100% this week", first.title)
    }

    @Test
    fun `pain words point to a physio or doctor, and come first`() {
        val found = tips(DiaryType.REHAB, listOf(day(1, set("SLR", 1.0))), entries = mapOf(1L to "My calves muscle feels some sournes", 3L to "Knee pain after lunges"))
        assertEquals("Pain or soreness on 2 days", found.first().title)
        assertTrue(found.first().body.contains("physio or doctor"))
        assertFalse(tips(entries = mapOf(1L to "Painted the wall")).any { it.kind == TipKind.CARE })
    }

    @Test
    fun `unreviewed workouts get a reminder to review`() {
        val days = (0L..4L).map { day(it, set("Squat", 20.0), confirmed = false) }
        assertTrue("5 workouts not reviewed" in titles(tips(workouts = days)))
    }

    @Test
    fun `food week with fried snacks, little water and low protein`() {
        val week = listOf(
            "2 vada, 1 samosa\n1 L water\n2 idli",
            "50 g pakoda\n1 L water\n2 dosa",
            "100 g chips\n1 L water\nrice, rasam"
        ).joinToString("\n---\n")
        val found = titles(tips(DiaryType.FOOD, food = week))
        assertTrue(found.toString(), "4 fried items this week" in found)
        assertTrue(found.toString(), "About 1 L water a day" in found)
        assertTrue(found.toString(), found.any { it.startsWith("Protein about") })
    }

    @Test
    fun `food tips need three logged days, and gym diaries get none`() {
        assertEquals(emptyList<CoachTip>(), tips(DiaryType.FOOD, food = "2 vada\n---\n2 samosa"))
        val week = listOf("2 vada, 1 samosa", "2 vada", "50 g pakoda").joinToString("\n---\n")
        assertTrue(tips(DiaryType.GYM, food = week).isEmpty())
        assertTrue("No water logged this week" in titles(tips(DiaryType.FOOD, food = week)))
    }
}
