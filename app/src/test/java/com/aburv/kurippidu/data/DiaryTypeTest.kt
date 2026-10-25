package com.aburv.kurippidu.data

import com.aburv.kurippidu.templatesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryTypeTest {

    @Test
    fun `stored keys read back, unknown ones are plain`() {
        DiaryType.entries.forEach { assertEquals(it, DiaryType.fromKey(it.name)) }
        assertEquals(DiaryType.PLAIN, DiaryType.fromKey(null))
        assertEquals(DiaryType.PLAIN, DiaryType.fromKey("SWIM"))
    }

    @Test
    fun `type is guessed from older diary names`() {
        assertEquals(DiaryType.GYM, DiaryType.guessFromTitle("Workout"))
        assertEquals(DiaryType.REHAB, DiaryType.guessFromTitle("Knee physio"))
        assertEquals(DiaryType.FOOD, DiaryType.guessFromTitle("What I eat"))
        assertEquals(DiaryType.PLAIN, DiaryType.guessFromTitle("Thoughts"))
    }

    @Test
    fun `every type has templates, and blanks are never numbers`() {
        // "20 kg" left in by mistake would later be counted as a real set
        val amount = Regex("\\d+\\s*(kg|rep|set|min|L\\b|°)")
        DiaryType.entries.forEach { type ->
            val templates = templatesFor(type)
            assertTrue(type.name, templates.isNotEmpty())
            templates.forEach { assertTrue(it.body, !amount.containsMatchIn(it.body)) }
            // "__" is underline markup in the editor
            templates.forEach { assertTrue(it.body, "__" !in it.body) }
        }
        assertEquals(listOf("Breakfast", "Lunch", "Snacks", "Dinner", "Water"), templatesFor(DiaryType.FOOD).map { it.label })
    }
}
