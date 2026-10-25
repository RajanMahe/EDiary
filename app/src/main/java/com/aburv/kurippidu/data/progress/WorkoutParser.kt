package com.aburv.kurippidu.data.progress

/** Which side a set was done on; BOTH also covers "not said". */
enum class Side { LEFT, RIGHT, BOTH }

/**
 * One line of a workout as the parser understood it. Numbers that were not
 * written stay null: "Left 3 set with 1kg cuff" has no reps.
 */
data class ParsedSet(
    val exercise: String,
    val side: Side = Side.BOTH,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val sets: Int = 1,
    val minutes: Double? = null,
    val warmUp: Boolean = false,
    val sourceLine: String = ""
) {
    /** kg × reps × sets; 0 when weight or reps are missing. */
    val volume: Double get() = (weightKg ?: 0.0) * (reps ?: 0) * sets
}

data class ParsedWorkout(val sets: List<ParsedSet>, val notes: List<String>)

/**
 * Reads free-text gym and rehab entries the way people actually write them:
 *
 *     2.Hamstring curl
 *     26 kg 3 set 8 rep left leg alone
 *     Quad using machine
 *     12.5 - 15
 *     20 - 15 2 set
 *     Front rise 5kg 1set 8 rep and 7.5kg 2 set 8 rep
 *     10 mins jog with 4.5 speed in treadmil
 *
 * A line with no numbers names the exercise for the lines below it. A line
 * with numbers is one or more sets. Anything it can't read is kept as a note,
 * so nothing written is lost; the user reviews the result before it counts.
 */
object WorkoutParser {

    private const val NUM = """(\d+(?:\.\d+)?)"""
    private val numberWords = mapOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10"
    )

    private val weightRe = Regex("""$NUM\s*(?:kg|kgs|kilo)\b""", RegexOption.IGNORE_CASE)
    private val repsRe = Regex("""(\d+)\s*(?:reps?|counts?|times)\b""", RegexOption.IGNORE_CASE)
    private val setsRe = Regex("""(\d+)\s*(?:sets?)\b""", RegexOption.IGNORE_CASE)
    private val minutesRe = Regex("""$NUM\s*(?:mins?|minutes?)\b""", RegexOption.IGNORE_CASE)

    /** "12.5 - 15", "15 kg - 15", "20 - 15 2 set": weight, dash, reps. */
    private val dashRe = Regex("""^$NUM\s*(?:kg)?\s*[-–]\s*(\d+)(?!\s*sets?\b)""", RegexOption.IGNORE_CASE)

    /** "3 × 10", "2 x 20": sets × reps. */
    private val timesRe = Regex("""\b(\d+)\s*[×x]\s*(\d+)\b""")
    private val eachHandRe = Regex("""$NUM\s*(?:kg)?\s*each\s+hand""", RegexOption.IGNORE_CASE)
    private val bodyWeightRe = Regex("""body\s*weight\s*[:=-]\s*\d""", RegexOption.IGNORE_CASE)
    private const val MAX_SET_WORDS = 16

    /** "Left ,right leg calf rise - 25,32" or "Inside tapping right, left - 50,50". */
    private val pairRe = Regex(
        """^(.*?)\b(left|right)\s*,?\s*(?:and\s+)?(right|left)\b(.*?)[-–:]\s*$NUM\s*(?:kg|reps?|cm)?\s*,\s*$NUM""",
        RegexOption.IGNORE_CASE
    )

    private fun leftRightPair(line: String): Pair<String?, List<Pair<Side, Double>>>? {
        val m = pairRe.find(line) ?: return null
        val (before, first, second, after, a, b) = m.destructured
        if (first.equals(second, ignoreCase = true)) return null
        val firstSide = if (first.equals("left", true)) Side.LEFT else Side.RIGHT
        val secondSide = if (firstSide == Side.LEFT) Side.RIGHT else Side.LEFT
        val name = nameOf("$before $after")
        return name to listOf(firstSide to a.toDouble(), secondSide to b.toDouble())
    }

    private val leadingWeightRe = Regex("""^$NUM\s*(?:kg)?\s*[-–]""", RegexOption.IGNORE_CASE)

    /** "Double leg 17.5,20": a list of weights. */
    private val weightListRe = Regex("""$NUM\s*(?:kg)?\s*,\s*$NUM""")

    private val numberingRe = Regex("""^\s*\d+\s*[.)]\s*(?=\D)""")
    private val separatorRe = Regex("""^[\s\-–—_=*·.]+$""")

    private val cardio = listOf(
        "reverse walk" to "Reverse walk", "walk" to "Walk", "jog" to "Jog", "run" to "Run",
        "cycling" to "Cycling", "cycle" to "Cycling", "swim" to "Swim", "skipping" to "Skipping",
        "elliptical" to "Elliptical", "rowing" to "Rowing"
    )

    /** Words that describe how or which side, not which exercise. */
    private val filler = setOf(
        "a", "alone", "and", "arm", "arms", "bar", "body", "bodyweight", "both", "done", "double",
        "each", "empty", "hand", "hands", "have", "i", "in", "leg", "legs", "left", "machine",
        "of", "on", "one", "only", "opposite", "right", "side", "single", "started", "the",
        "today", "used", "using", "weight", "weights", "with", "had", "added", "also", "landing",
        "warm", "up", "warmup", "plate", "plates", "support"
    )

    private val proseStart = Regex("""^(i|i'm|im|and|but|today|then|so|it|my|felt|feeling)\b""", RegexOption.IGNORE_CASE)

    fun parse(text: String): ParsedWorkout {
        val sets = mutableListOf<ParsedSet>()
        val notes = mutableListOf<String>()
        var exercise = "Workout"
        var side = Side.BOTH

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || separatorRe.matches(line)) continue
            val body = line.replace(numberingRe, "").trim()
            val words = wordNumbersToDigits(body)
            val hasAmount = weightRe.containsMatchIn(words) || repsRe.containsMatchIn(words) ||
                setsRe.containsMatchIn(words) || minutesRe.containsMatchIn(words) ||
                dashRe.containsMatchIn(words) || weightListRe.containsMatchIn(words) || timesRe.containsMatchIn(words)

            // Copied instructions and body measurements are kept as notes, not counted as sets
            if (body.split(Regex("\\s+")).size > MAX_SET_WORDS || bodyWeightRe.containsMatchIn(body)) {
                notes += line
                continue
            }

            if (!hasAmount) {
                val lineSide = sideOf(words)
                when {
                    // "Left side alone", "In right leg one set": only changes the side
                    lineSide != null && nameOf(words) == null -> side = lineSide
                    proseStart.containsMatchIn(body) || body.split(Regex("\\s+")).size > 6 || body.any(Char::isDigit) -> {
                        lineSide?.let { side = it }
                        notes += line
                    }
                    else -> {
                        exercise = headingName(body)
                        side = Side.BOTH
                    }
                }
                continue
            }

            // "In right leg one set": sets the side for the lines below, isn't a set itself
            if (isContextOnly(words)) {
                sideOf(words)?.let { side = it }
                continue
            }

            // "Left, right leg calf rise - 25,32": one number per side
            val leftRight = leftRightPair(words)
            if (leftRight != null) {
                val (name, pair) = leftRight
                val what = if (proseStart.containsMatchIn(body)) exercise else name ?: exercise
                pair.forEach { (pairSide, value) ->
                    sets += if (weightRe.containsMatchIn(words)) ParsedSet(what, pairSide, weightKg = value, sourceLine = line)
                    else ParsedSet(what, pairSide, reps = value.toInt(), sourceLine = line)
                }
                continue
            }

            // A prose line ("And for lunges … 10kg") never renames the exercise
            val prose = proseStart.containsMatchIn(body)
            var lineName: String? = null // "Front rise 5kg … and 7.5kg …": the name carries over
            val setsBefore = sets.size
            for (segment in segmentsOf(words)) {
                val found = parseSegment(segment, "", side, line)
                if (found.isEmpty()) continue // "land on box": no numbers, no new name
                if (!prose) lineName = nameOf(segment) ?: lineName
                // A line naming its own exercise doesn't inherit the side from the lines above
                val segmentSide = sideOf(segment) ?: if (lineName != null) Side.BOTH else side
                sets += found.map { set ->
                    set.copy(exercise = set.exercise.ifEmpty { lineName ?: exercise }, side = segmentSide)
                }
            }
            if (sets.size == setsBefore) notes += line // "Total time: 50–60 minutes"
        }
        return ParsedWorkout(sets, notes)
    }

    private fun parseSegment(segment: String, exercise: String, side: Side, line: String): List<ParsedSet> {
        val warmUp = Regex("""warm\s*-?\s*up""", RegexOption.IGNORE_CASE).containsMatchIn(segment)
        val minutes = minutesRe.find(segment)?.groupValues?.get(1)?.toDouble()
        if (minutes != null && !Regex("""\b(total|part)\b""", RegexOption.IGNORE_CASE).containsMatchIn(segment)) {
            val activity = cardio.firstOrNull { (word, _) -> Regex("""\b$word""", RegexOption.IGNORE_CASE).containsMatchIn(segment) }?.second
            return listOf(ParsedSet(activity ?: exercise, side, minutes = minutes, warmUp = warmUp, sourceLine = line))
        }

        timesRe.find(segment)?.let { m ->
            return listOf(
                ParsedSet(
                    exercise, side, weightKg = weightRe.find(segment)?.groupValues?.get(1)?.toDouble(),
                    reps = m.groupValues[2].toInt(), sets = m.groupValues[1].toInt(), warmUp = warmUp, sourceLine = line
                )
            )
        }
        val sets = setsRe.find(segment)?.groupValues?.get(1)?.toInt()
        dashRe.find(segment.trim())?.let { m ->
            return listOf(
                ParsedSet(
                    exercise, side, weightKg = m.groupValues[1].toDouble(), reps = m.groupValues[2].toInt(),
                    sets = sets ?: 1, warmUp = warmUp, sourceLine = line
                )
            )
        }
        weightListRe.find(segment)?.takeIf { !weightRe.containsMatchIn(segment) || segment.contains(",") }?.let {
            val weights = Regex(NUM).findAll(segment.substring(it.range.first)).map { n -> n.value.toDouble() }.toList()
            return weights.map { w -> ParsedSet(exercise, side, weightKg = w, sets = 1, warmUp = warmUp, sourceLine = line) }
        }

        val weight = weightRe.find(segment)?.groupValues?.get(1)?.toDouble()
            ?: eachHandRe.find(segment)?.groupValues?.get(1)?.toDouble() // "2.5 each hand 15 reps"
            ?: leadingWeightRe.find(segment.trim())?.groupValues?.get(1)?.toDouble() // "10 - 1 set 12 rep"
        val reps = repsRe.find(segment)?.groupValues?.get(1)?.toInt()
        if (weight == null && reps == null && sets == null) return emptyList()
        return listOf(ParsedSet(exercise, side, weight, reps, sets ?: 1, warmUp = warmUp, sourceLine = line))
    }

    /**
     * Splits "5kg 1set 8 rep and 7.5kg 2 set 8 rep" and
     * "1set 19 kg plate 10 rep 2 set 26 kg plate 10 rep" into one piece per group.
     */
    private fun segmentsOf(line: String): List<String> {
        val byAnd = line.split(Regex("""\s+and\s+""", RegexOption.IGNORE_CASE))
        return byAnd.flatMap { part ->
            val starts = setsRe.findAll(part).map { it.range.first }.toList()
            val cuts = starts.filter { start ->
                val before = part.substring(0, start)
                val after = part.substring(start)
                // A new group only when another weight follows: "… 10 rep 3 set" is still one group
                start > 0 && repsRe.containsMatchIn(before) && weightRe.containsMatchIn(before) &&
                    weightRe.containsMatchIn(after)
            }
            if (cuts.isEmpty()) listOf(part)
            else (listOf(0) + cuts + part.length).zipWithNext { a, b -> part.substring(a, b) }
        }.filter { it.isNotBlank() }
    }

    /** "Sumo squat with 10 kg …" → "Sumo squat"; "Left 3 set …" → null (side only). */
    private fun nameOf(segment: String): String? {
        val beforeNumber = segment.split(Regex("""\d""")).first()
        val kept = beforeNumber.split(Regex("""[^\p{L}']+""")).filter { it.isNotBlank() && it.lowercase() !in filler }
        if (kept.isEmpty()) return null
        return kept.joinToString(" ").replaceFirstChar { it.uppercase() }
    }

    private fun headingName(line: String): String =
        line.removePrefix("In ").removePrefix("in ").trim().trimEnd(':', '-', ' ')
            .replaceFirstChar { it.uppercase() }

    private fun isContextOnly(line: String): Boolean =
        setsRe.containsMatchIn(line) && !weightRe.containsMatchIn(line) && !repsRe.containsMatchIn(line) &&
            !minutesRe.containsMatchIn(line) && !dashRe.containsMatchIn(line) && !leadingWeightRe.containsMatchIn(line.trim()) &&
            sideOf(line) != null && nameOf(line) == null

    private fun sideOf(text: String): Side? {
        val t = text.lowercase()
        // "left leg alone … support with right leg": the "alone" side is the one worked
        Regex("""\b(left|right)\s+(?:leg\s+|side\s+|hand\s+|arm\s+)?alone\b""").find(t)?.let {
            return if (it.groupValues[1] == "left") Side.LEFT else Side.RIGHT
        }
        val left = Regex("""\bleft\b""").containsMatchIn(t)
        val right = Regex("""\bright\b""").containsMatchIn(t)
        return when {
            left && !right -> Side.LEFT
            right && !left -> Side.RIGHT
            Regex("""\b(both|double|each side)\b""").containsMatchIn(t) -> Side.BOTH
            else -> null
        }
    }

    private fun wordNumbersToDigits(text: String): String =
        text.replace(Regex("""\b(one|two|three|four|five|six|seven|eight|nine|ten)\b(?=\s*(?:sets?|reps?)\b)""", RegexOption.IGNORE_CASE)) {
            numberWords.getValue(it.value.lowercase())
        }
}
