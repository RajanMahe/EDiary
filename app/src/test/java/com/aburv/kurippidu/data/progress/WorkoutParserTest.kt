package com.aburv.kurippidu.data.progress

import com.aburv.kurippidu.data.progress.Side.BOTH
import com.aburv.kurippidu.data.progress.Side.LEFT
import com.aburv.kurippidu.data.progress.Side.RIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lines are the way they're really written in the gym and rehab diaries. */
class WorkoutParserTest {

    /** exercise | side | kg | reps | sets | minutes */
    private fun ParsedSet.short() =
        listOf(exercise, side, weightKg, reps, sets, minutes).joinToString(" | ")

    private fun parse(text: String) = WorkoutParser.parse(text.trimIndent())

    private fun assertSets(text: String, vararg expected: String) =
        assertEquals(expected.toList(), parse(text).sets.map { it.short() })

    @Test
    fun `numbered heading, warm-up, then one side`() {
        val workout = parse(
            """
            1.Leg Press - single

            Warm up each side 20 kg 8 rep

            Left side alone
            20kg 10 rep
            30kg 10 rep
            35 kg 10 rep
            """
        )
        assertEquals(
            listOf(
                "Leg Press - single | BOTH | 20.0 | 8 | 1 | null",
                "Leg Press - single | LEFT | 20.0 | 10 | 1 | null",
                "Leg Press - single | LEFT | 30.0 | 10 | 1 | null",
                "Leg Press - single | LEFT | 35.0 | 10 | 1 | null"
            ),
            workout.sets.map { it.short() }
        )
        assertTrue(workout.sets.first().warmUp)
    }

    @Test
    fun `the side done alone wins over a supporting side`() = assertSets(
        """
        2.Hamstring curl
        33kg warm up 8 rep both leg
        26 kg 3 set 8 rep left leg alone last 3 count support with right leg
        """,
        "Hamstring curl | BOTH | 33.0 | 8 | 1 | null",
        "Hamstring curl | LEFT | 26.0 | 8 | 3 | null"
    )

    @Test
    fun `side written after the weight`() = assertSets(
        """
        3.Single leg RDL
        16kg left leg 3 set 8 rep
        16kg right leg 1 set 8 rep
        """,
        "Single leg RDL | LEFT | 16.0 | 8 | 3 | null",
        "Single leg RDL | RIGHT | 16.0 | 8 | 1 | null"
    )

    @Test
    fun `exercise named on the same line`() = assertSets(
        """
        4.Calf Raises
        Standing calf raise 20 kg 10 rep 3 set
        Sumo squat with 7.5 kg 3 set 15 rep
        Shoulder press machine each side 2.5 kg 10 rep 3 set
        Bench dip 3 set 10 rep each
        """,
        "Standing calf raise | BOTH | 20.0 | 10 | 3 | null",
        "Sumo squat | BOTH | 7.5 | 15 | 3 | null",
        "Shoulder press | BOTH | 2.5 | 10 | 3 | null",
        "Bench dip | BOTH | null | 10 | 3 | null"
    )

    @Test
    fun `two groups on one line`() = assertSets(
        """
        Front rise 5kg 1set 8 rep and 7.5kg 2 set 8 rep
        Cable pull down 1set 19 kg plate 10 rep 2 set 26 kg plate 10 rep
        """,
        "Front rise | BOTH | 5.0 | 8 | 1 | null",
        "Front rise | BOTH | 7.5 | 8 | 2 | null",
        "Cable pull down | BOTH | 19.0 | 10 | 1 | null",
        "Cable pull down | BOTH | 26.0 | 10 | 2 | null"
    )

    @Test
    fun `cardio lines`() = assertSets(
        """
        5 mins walk incline level 11 speed 3km/h in threadmil
        5 mins reverse walk incline level 11 speed 3 km/h
        10 mins jog with 4.5 speed in treadmil
        10 mins cycling
        """,
        "Walk | BOTH | null | null | 1 | 5.0",
        "Reverse walk | BOTH | null | null | 1 | 5.0",
        "Jog | BOTH | null | null | 1 | 10.0",
        "Cycling | BOTH | null | null | 1 | 10.0"
    )

    @Test
    fun `weight dash reps, as on the quad machine`() = assertSets(
        """
        Quad using machine
        15 kg - 15 3 set
        12.5 - 15
        20 - 15 2 set
        10 - 1 set 12 rep
        Double leg 17.5,20
        """,
        "Quad using machine | BOTH | 15.0 | 15 | 3 | null",
        "Quad using machine | BOTH | 12.5 | 15 | 1 | null",
        "Quad using machine | BOTH | 20.0 | 15 | 2 | null",
        "Quad using machine | BOTH | 10.0 | 12 | 1 | null",
        "Quad using machine | BOTH | 17.5 | null | 1 | null",
        "Quad using machine | BOTH | 20.0 | null | 1 | null"
    )

    @Test
    fun `rehab log with sides, prose and notes`() {
        val workout = parse(
            """
            In SLR
            4 different variation
            Left 3 set with 1kg cuff
            Right 2 set with 1kg cuff

            Lunges
            I have started using weight in one hand with 7.5 kg with 15 reps 3 set
            Body weight 15 reps 3 set

            And i have done single leg quad using machine in left leg
            7.5 - 15
            In right leg one set
            12.5 -15

            I get shaking only at the end for all the exercise i could see the improvement
            """
        )
        assertEquals(
            listOf(
                "SLR | LEFT | 1.0 | null | 3 | null",
                "SLR | RIGHT | 1.0 | null | 2 | null",
                "Lunges | BOTH | 7.5 | 15 | 3 | null",
                "Lunges | BOTH | null | 15 | 3 | null",
                "Lunges | LEFT | 7.5 | 15 | 1 | null",
                "Lunges | RIGHT | 12.5 | 15 | 1 | null"
            ),
            workout.sets.map { it.short() }
        )
        // Nothing written is lost
        assertEquals(
            listOf(
                "4 different variation",
                "And i have done single leg quad using machine in left leg",
                "I get shaking only at the end for all the exercise i could see the improvement"
            ),
            workout.notes
        )
    }

    @Test
    fun `box jumps and hops counted by reps`() = assertSets(
        """
        5.Box jumps
        18 inch box
        double leg landing 8 count
        Left leg landing 8 count
        Front hop
        with double leg one set 20 rep
        Single leg left alone 20 rep 3 set
        """,
        "Box jumps | BOTH | null | 8 | 1 | null",
        "Box jumps | LEFT | null | 8 | 1 | null",
        "Front hop | BOTH | null | 20 | 1 | null",
        "Front hop | LEFT | null | 20 | 3 | null"
    )

    @Test
    fun `separators, blanks and template leftovers are ignored`() {
        val workout = parse(
            """
            -----------------------------------
            ———————————————

            🏋️ Exercise:
            Name:
            … kg … rep … set
            """
        )
        assertEquals(emptyList<ParsedSet>(), workout.sets)
    }

    @Test
    fun `volume is kg x reps x sets`() {
        assertEquals(480.0, ParsedSet("Squat", weightKg = 20.0, reps = 8, sets = 3).volume, 0.0)
        assertEquals(0.0, ParsedSet("Plank", reps = null, sets = 3).volume, 0.0)
        assertEquals(BOTH, ParsedSet("x").side)
        assertTrue(LEFT != RIGHT)
    }

    @Test
    fun `left and right numbers on one line`() = assertSets(
        """
        Left ,right leg calf rise - 25,32
        Inside tapping right, left - 50,48
        Left ,right single leg sit to stand (42 cm chair)- 14,16
        """,
        "Calf rise | LEFT | null | 25 | 1 | null",
        "Calf rise | RIGHT | null | 32 | 1 | null",
        "Inside tapping | RIGHT | null | 50 | 1 | null",
        "Inside tapping | LEFT | null | 48 | 1 | null",
        "Sit to stand | LEFT | null | 14 | 1 | null",
        "Sit to stand | RIGHT | null | 16 | 1 | null"
    )

    @Test
    fun `sets x reps, each hand, and lines that stay notes`() {
        val workout = parse(
            """
            Bench dumbbell press
            2.5 each hand 15 reps
            Split Squat (Bodyweight)	3 × 10 each	Left leg slow
            8 count jump and land on box and 8 count land on floor
            1. Body Weight: 71.5 kg
            Total time: 50–60 minutes
            1.3) Glute Bridges: Lie on your back and lift your hips. This stabilizes your pelvis for the landing. (2 sets of 15 reps). - Done
            """
        )
        assertEquals(
            listOf(
                "Bench dumbbell press | BOTH | 2.5 | 15 | 1 | null",
                "Split Squat | LEFT | null | 10 | 3 | null",
                "Bench dumbbell press | BOTH | null | 8 | 1 | null",
                "Bench dumbbell press | BOTH | null | 8 | 1 | null"
            ),
            workout.sets.map { it.short() }
        )
        assertEquals(3, workout.notes.size)
    }
}
