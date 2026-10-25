package com.aburv.kurippidu

import com.aburv.kurippidu.data.DiaryType

/**
 * A heading (and optional lines to fill in) inserted from the "+" sheet.
 * Blanks are "…" (not "__", which is underline markup) so an unfilled template never looks like real numbers.
 */
data class DiaryTemplate(
    val label: String,
    val emoji: String,
    val body: String = ""
)

/** The "+" sheet's choices for a kind of diary. */
fun templatesFor(type: DiaryType): List<DiaryTemplate> = when (type) {
    DiaryType.GYM -> listOf(
        DiaryTemplate("Exercise", "🏋️", "Name: \n… kg … rep … set\n"),
        DiaryTemplate("Single leg / arm", "🦵", "Name: \nLeft … kg … rep … set\nRight … kg … rep … set\n"),
        DiaryTemplate("Cardio", "🏃", "… mins walk / jog / cycle, speed …, incline …\n"),
        DiaryTemplate("Warm-up", "🔥", "… mins \n")
    )
    DiaryType.REHAB -> listOf(
        DiaryTemplate("Exercise (Left / Right)", "🦵", "Name: \nLeft … set … rep … kg\nRight … set … rep … kg\n"),
        DiaryTemplate("Pain", "😣", "Pain (0–10): …\nWhere: \n"),
        DiaryTemplate("Range of motion", "📐", "Left …° · Right …°\n"),
        DiaryTemplate("Note for physio", "📝")
    )
    DiaryType.FOOD -> listOf(
        DiaryTemplate("Breakfast", "🌅"),
        DiaryTemplate("Lunch", "🍛"),
        DiaryTemplate("Snacks", "🍪"),
        DiaryTemplate("Dinner", "🌙"),
        DiaryTemplate("Water", "💧", "… L\n")
    )
    DiaryType.PLAIN -> listOf(
        DiaryTemplate("Workout", "🏋️"),
        DiaryTemplate("Food", "🍔"),
        DiaryTemplate("Spent", "💸")
    )
}
