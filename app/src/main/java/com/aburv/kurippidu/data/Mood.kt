package com.aburv.kurippidu.data

/** One-tap mood for a day. [key] is what's stored in the database and backups. */
enum class Mood(val key: String, val emoji: String, val label: String, val color: Long) {
    GREAT("great", "😄", "Great", 0xFF4CAF50),
    GOOD("good", "🙂", "Good", 0xFF8BC34A),
    OKAY("okay", "😐", "Okay", 0xFFFFC107),
    LOW("low", "😔", "Low", 0xFFFF9800),
    SAD("sad", "😢", "Sad", 0xFF5C7CFA);

    companion object {
        fun fromKey(key: String?): Mood? = entries.firstOrNull { it.key == key }
    }
}
