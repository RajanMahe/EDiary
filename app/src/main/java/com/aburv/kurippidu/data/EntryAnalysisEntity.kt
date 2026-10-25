package com.aburv.kurippidu.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.aburv.kurippidu.data.progress.FoodDay
import com.aburv.kurippidu.data.progress.FoodItem
import com.aburv.kurippidu.data.progress.FoodParser
import com.aburv.kurippidu.data.progress.IndianFoodTable
import com.aburv.kurippidu.data.progress.ParsedSet
import com.aburv.kurippidu.data.progress.Side
import com.aburv.kurippidu.data.progress.WorkoutParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * What the parsers read from one entry, kept so Progress doesn't read every
 * entry again each time it opens. It is only a cache: [contentHash] and
 * [parserVersion] say when it no longer matches and must be read again, and
 * it is never backed up (a restore simply rebuilds it).
 */
@Entity(
    tableName = "entry_analysis",
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EntryAnalysisEntity(
    @PrimaryKey
    val entryId: Int,
    val contentHash: Int,
    val parserVersion: Int,
    /** JSON list of [StoredSet]. */
    val workout: String,
    /** JSON list of [StoredFood]. */
    val food: String
)

/** One entry's workout sets and food, as the parsers read them. */
data class EntryAnalysis(val sets: List<ParsedSet>, val food: FoodDay) {

    fun toEntity(entryId: Int, content: String) = EntryAnalysisEntity(
        entryId = entryId,
        contentHash = content.hashCode(),
        parserVersion = PARSER_VERSION,
        workout = json.encodeToString(sets.map { StoredSet(it.exercise, it.side.name, it.weightKg, it.reps, it.sets, it.minutes, it.warmUp) }),
        // Only the amount is kept; calories come from the food table when read back
        food = json.encodeToString(food.items.map { StoredFood(it.text, it.food?.name, it.grams) })
    )

    companion object {
        /**
         * Raise this whenever the workout or food parser changes what it reads,
         * so every entry is read again with the new rules.
         */
        const val PARSER_VERSION = 1

        private val json = Json { ignoreUnknownKeys = true }

        fun of(content: String) = EntryAnalysis(WorkoutParser.parse(content).sets, FoodParser.parseDay(content))

        /** The stored reading, or null when it's out of date for [content]. */
        fun from(entity: EntryAnalysisEntity, content: String): EntryAnalysis? {
            if (entity.contentHash != content.hashCode() || entity.parserVersion != PARSER_VERSION) return null
            return runCatching {
                val sets = json.decodeFromString<List<StoredSet>>(entity.workout).map {
                    ParsedSet(it.exercise, Side.entries.firstOrNull { s -> s.name == it.side } ?: Side.BOTH, it.weightKg, it.reps, it.sets, it.minutes, it.warmUp)
                }
                val items = json.decodeFromString<List<StoredFood>>(entity.food).map {
                    val food = it.food?.let(IndianFoodTable::byName)
                    FoodItem(it.text, food, it.grams, food?.let { f -> it.grams * f.kcalPer100 / 100 } ?: 0.0, food?.let { f -> it.grams * f.proteinPer100 / 100 } ?: 0.0)
                }
                EntryAnalysis(sets, FoodDay(items))
            }.getOrNull()
        }
    }
}

@Serializable
internal data class StoredSet(
    val exercise: String,
    val side: String,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val sets: Int = 1,
    val minutes: Double? = null,
    val warmUp: Boolean = false
)

@Serializable
internal data class StoredFood(val text: String, val food: String? = null, val grams: Double)
