package com.aburv.kurippidu.data.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lines as they're really written in the food diary. */
class FoodParserTest {

    /** food name | grams */
    private fun items(line: String) =
        FoodParser.parseLine(line).map { "${it.food?.name ?: "?" + it.text} | ${it.grams.toInt()}" }

    @Test
    fun `two drinks with amounts on one line`() = assertEquals(
        listOf("Pathaneer | 250", "Tender coconut | 250"),
        items("250 ml pathaneer 250 ml of ilani")
    )

    @Test
    fun `grams and ml joined by and`() = assertEquals(
        listOf("Lemon rice | 150", "Potato fry | 50", "Buttermilk | 100"),
        items("150g lemon rice and 50g potato fry and 100 ml butter milk")
    )

    @Test
    fun `counted pieces and sides`() = assertEquals(
        listOf("Parotta | 240", "Kurma | 100", "Chicken curry | 150", "Egg | 50"),
        items("3 parota with gravy and chicken and egg")
    )

    @Test
    fun `litres of water`() {
        val day = FoodParser.parseDay("2L water")
        assertEquals(2.0, day.waterLitres, 0.0)
        assertEquals(0, day.kcal)
    }

    @Test
    fun `shared amount is split between drinks`() = assertEquals(
        listOf("Tender coconut | 250", "Pathaneer | 250"),
        items("Ilaneer+pathaneer 500 ml")
    )

    @Test
    fun `spoons, pieces, sizes and number words`() {
        assertEquals(listOf("Chicken rice | 15", "Chips | 8"), items("One table spoon chicken rice, 4 peices of lays"))
        assertEquals(listOf("Puff | 90", "Bun | 49"), items("One chicken puff , one small size coconut bun with cream"))
        assertEquals(listOf("Lemon juice | 250", "Mini vada | 60"), items("250 ml lemon juice and 4 peices mini vada"))
    }

    @Test
    fun `amount after the food`() = assertEquals(
        listOf("Rice | 100", "Beetroot poriyal | 50", "Fish fry | 30"),
        items("100g rice , beetroot 50g ,30 g fish fry")
    )

    @Test
    fun `a full day adds up, with fried and sweet counts`() {
        val day = FoodParser.parseDay(
            """
            1 egg poriyal and 250 ml ilaneer
            50 g inipusev
            50 g pakoda
            10g butter biscuit
            -----------------------------------
            """.trimIndent()
        )
        assertEquals(5, day.items.size)
        assertEquals(2, day.friedCount) // inippu sev, pakoda
        assertEquals(2, day.sweetCount) // inippu sev, biscuit
        // 126 + 48 + 240 + 225 + 47
        assertTrue(day.kcal in 650..720)
        assertTrue(day.proteinG in 15..25)
        assertEquals(emptyList<String>(), day.unknown)
    }

    @Test
    fun `unknown food is listed, not dropped`() {
        val day = FoodParser.parseDay("2 kozhukattai")
        assertEquals(listOf("2 kozhukattai"), day.unknown)
        assertNull(day.items.single().food)
    }

    @Test
    fun `template headings carry no food`() {
        assertEquals(emptyList<String>(), items("🌅 Breakfast:"))
        assertEquals(listOf("Idli | 120", "Sambar | 150"), items("🌅 Breakfast: 3 idli, sambar"))
    }

    @Test
    fun `longest name wins`() {
        assertEquals("Lemon rice", IndianFoodTable.find("lemon rice")?.name)
        assertEquals("Fish fry", IndianFoodTable.find("fish fry")?.name)
        assertEquals("Rice", IndianFoodTable.find("plain rice")?.name)
        assertNull(IndianFoodTable.find("riceberry"))
    }

    @Test
    fun `table has no duplicate aliases`() {
        val aliases = IndianFoodTable.foods.flatMap { it.aliases }
        assertEquals(aliases.groupBy { it }.filter { it.value.size > 1 }.keys, emptySet<String>())
        assertTrue(IndianFoodTable.foods.size >= 100)
    }
}
