package com.aburv.kurippidu.data.progress

import kotlin.math.roundToInt

/** One thing eaten or drunk, with estimated amounts. [food] is null when it isn't in the table. */
data class FoodItem(
    val text: String,
    val food: Food?,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double
) {
    val isWater get() = food?.tags?.contains(FoodTag.WATER) == true
    val isFried get() = food?.tags?.contains(FoodTag.FRIED) == true
    val isSweet get() = food?.tags?.contains(FoodTag.SWEET) == true
}

/** A day's food added up. Everything here is an estimate. */
data class FoodDay(val items: List<FoodItem>) {
    val kcal get() = items.sumOf { it.kcal }.roundToInt()
    val proteinG get() = items.sumOf { it.proteinG }.roundToInt()
    val waterLitres get() = items.filter { it.isWater }.sumOf { it.grams } / 1000.0
    val friedCount get() = items.count { it.isFried }
    val sweetCount get() = items.count { it.isSweet }
    val unknown get() = items.filter { it.food == null }.map { it.text }
}

/**
 * Reads food lines as people write them:
 *
 *     250 ml pathaneer 250 ml of ilani
 *     150g lemon rice and 50g potato fry and 100 ml butter milk
 *     3 parota with gravy and chicken and egg
 *     Ilaneer+pathaneer 500 ml
 *     One table spoon chicken rice, 4 peices of lays
 *     2L water
 */
object FoodParser {

    private const val NUM = """(\d+(?:\.\d+)?)"""

    private val numberWords = mapOf(
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0,
        "six" to 6.0, "half" to 0.5, "quarter" to 0.25
    )

    /** Grams (or ml) in one of each unit; null means "count pieces". */
    private val units: List<Pair<Regex, Double?>> = listOf(
        """kg|kgs""" to 1000.0,
        """g|gm|gms|grams?""" to 1.0,
        """l|litres?|liters?|ltrs?""" to 1000.0,
        """ml|mls""" to 1.0,
        """table\s*spoons?|tbsps?""" to 15.0,
        """tea\s*spoons?|tsps?""" to 5.0,
        """cups?|glass(?:es)?|tumblers?""" to 250.0,
        """bowls?|katoris?""" to 200.0,
        """plates?""" to 300.0,
        """scoops?""" to 30.0,
        """pieces?|peices?|pcs|nos|no""" to null
    ).map { (pattern, grams) -> Regex("""^($pattern)\b\.?\s*(?:of\s+)?""", RegexOption.IGNORE_CASE) to grams }

    private val numberStart = Regex("""^$NUM\s*""")
    private val wordStart = Regex("""^(a|an|one|two|three|four|five|six|half|quarter)\s+""", RegexOption.IGNORE_CASE)
    private val sizeWords = Regex("""\b(small|mini|big|large|medium)\s*(size(d)?)?\b""", RegexOption.IGNORE_CASE)
    private val trailingAmount = Regex("""\s$NUM\s*(ml|l|g|gm|kg)\s*$""", RegexOption.IGNORE_CASE)
    private val separator = Regex("""^[\s\-–—_=*·.]+$""")

    fun parseDay(text: String): FoodDay =
        FoodDay(text.lines().map { it.trim() }.filter { it.isNotEmpty() && !separator.matches(it) }.flatMap(::parseLine))

    fun parseLine(line: String): List<FoodItem> {
        // Headings like "🌅 Breakfast:" carry no food
        val body = line.replace(Regex("""^[^\p{L}\d]*\p{L}+\s*:\s*"""), { m ->
            if (IndianFoodTable.find(m.value) == null) "" else m.value
        }).trim()
        if (body.isEmpty()) return emptyList()

        return splitItems(body).mapNotNull(::parseItem)
    }

    /** "250 ml pathaneer 250 ml of ilani" → two items; "a and b", "a, b", "a with b" too. */
    private fun splitItems(line: String): List<String> {
        val parts = line.split(Regex("""\s*(?:,|\band\b|&)\s*""", RegexOption.IGNORE_CASE))
            .flatMap { part ->
                // "3 parota with gravy": gravy is its own item; "bun with cream" stays one bun
                val (main, extras) = part.split(Regex("""\s+with\s+""", RegexOption.IGNORE_CASE)).let { it.first() to it.drop(1) }
                listOf(main) + extras.filter { IndianFoodTable.find(it) != null }
            }
            .flatMap { part ->
                // A second amount before another food starts a new item: "250 ml pathaneer 250 ml of ilani"
                part.split(Regex("""(?<=\p{L})\s+(?=\d+(?:\.\d+)?\s*(?:ml|l|g|gm|kg)\b\s*(?:of\s+)?\p{L})""", RegexOption.IGNORE_CASE))
            }
            .map { it.trim() }.filter { it.isNotEmpty() }

        // "Ilaneer+pathaneer 500 ml": the amount at the end is shared
        return parts.flatMap { part ->
            if (!part.contains('+')) return@flatMap listOf(part)
            val pieces = part.split('+').map { it.trim() }.filter { it.isNotEmpty() }
            val shared = trailingAmount.find(pieces.last())
            if (shared == null || pieces.size < 2) return@flatMap pieces
            val each = shared.groupValues[1].toDouble() / pieces.size
            val unit = shared.groupValues[2]
            val last = pieces.last().substring(0, shared.range.first).trim()
            (pieces.dropLast(1) + last).map { "${formatAmount(each)} $unit $it" }
        }
    }

    private fun parseItem(raw: String): FoodItem? {
        var rest = raw.trim().trimEnd('.', ';')
        var count: Double? = null
        var gramsPerUnit: Double? = null
        var hasUnit = false

        numberStart.find(rest)?.let { count = it.groupValues[1].toDouble(); rest = rest.substring(it.range.last + 1) }
            ?: wordStart.find(rest)?.let { count = numberWords[it.groupValues[1].lowercase()]; rest = rest.substring(it.range.last + 1) }

        for ((re, grams) in units) {
            val m = re.find(rest) ?: continue
            hasUnit = true
            gramsPerUnit = grams
            rest = rest.substring(m.range.last + 1)
            break
        }

        // "pathaneer 250 ml", "fish fry 30 g": amount written after the food
        if (count == null) {
            trailingAmount.find(" $rest")?.let { m ->
                count = m.groupValues[1].toDouble()
                gramsPerUnit = units.firstOrNull { (re, _) -> re.find(m.groupValues[2]) != null }?.second
                hasUnit = true
                rest = " $rest".substring(0, m.range.first).trim()
            }
        }

        val name = rest.replace(Regex("""^(of|size)\s+""", RegexOption.IGNORE_CASE), "").trim()
        if (name.isEmpty() || name.none(Char::isLetter)) return null

        val food = IndianFoodTable.find(name)
        val sizeWord = sizeWords.find(name)?.groupValues?.get(1)?.lowercase()
        // "mini vada" is already a small piece in the table
        val sizeInName = sizeWord != null && food?.aliases?.any { sizeWord in it && it in name.lowercase() } == true
        val size = when (sizeWord.takeUnless { sizeInName }) {
            "small", "mini" -> 0.7
            "big", "large" -> 1.3
            else -> 1.0
        }
        val grams = when {
            hasUnit && gramsPerUnit != null -> (count ?: 1.0) * gramsPerUnit!!
            count != null -> count!! * (food?.pieceG ?: 100.0) * size // "3 parota", "4 pieces of lays"
            else -> (food?.servingG ?: 100.0) * size
        }
        val kcal = food?.let { grams * it.kcalPer100 / 100 } ?: 0.0
        val protein = food?.let { grams * it.proteinPer100 / 100 } ?: 0.0
        return FoodItem(raw.trim(), food, grams, kcal, protein)
    }

    private fun formatAmount(value: Double) =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
