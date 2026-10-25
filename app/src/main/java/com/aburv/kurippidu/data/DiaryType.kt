package com.aburv.kurippidu.data

/** What a diary is for. Stored by [name] in `diaries.type`. */
enum class DiaryType(val label: String, val emoji: String, val suggestedTitle: String) {
    PLAIN("Plain", "📔", "My Diary"),
    GYM("Gym", "🏋️", "Gym log"),
    REHAB("Rehab", "🦵", "Rehab log"),
    FOOD("Food", "🍛", "Food diary");

    companion object {
        fun fromKey(key: String?): DiaryType = entries.firstOrNull { it.name == key } ?: PLAIN

        /** Best guess from a diary's name, for diaries made before types existed. */
        fun guessFromTitle(title: String): DiaryType {
            val t = title.lowercase()
            return when {
                listOf("rehab", "physio", "recovery", "acl", "knee").any { it in t } -> REHAB
                listOf("gym", "workout", "exercise", "fitness", "training").any { it in t } -> GYM
                listOf("food", "meal", "diet", "eat", "nutrition").any { it in t } -> FOOD
                else -> PLAIN
            }
        }
    }
}

val DiaryOwnerEntity.diaryType: DiaryType get() = DiaryType.fromKey(type)
