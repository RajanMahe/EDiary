package com.aburv.kurippidu.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aburv.kurippidu.data.progress.ParsedSet
import com.aburv.kurippidu.data.progress.Side

/** A set the user checked and confirmed on the "Review workout" sheet. */
@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["entryId"])]
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val entryId: Int,
    val position: Int,
    val exercise: String,
    val side: String,
    val weightKg: Double?,
    val reps: Int?,
    val sets: Int,
    val minutes: Double?,
    val warmUp: Boolean
) {
    fun toParsed() = ParsedSet(
        exercise, runCatching { Side.valueOf(side) }.getOrDefault(Side.BOTH), weightKg, reps, sets, minutes, warmUp
    )

    companion object {
        fun from(entryId: Int, position: Int, set: ParsedSet) = WorkoutSetEntity(
            entryId = entryId, position = position, exercise = set.exercise.trim(), side = set.side.name,
            weightKg = set.weightKg, reps = set.reps, sets = set.sets, minutes = set.minutes, warmUp = set.warmUp
        )
    }
}

/**
 * Marks an entry's workout as confirmed. [contentHash] is the text it was
 * confirmed against, so editing the entry later asks for a new review.
 */
@Entity(
    tableName = "workout_reviews",
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WorkoutReviewEntity(
    @PrimaryKey
    val entryId: Int,
    val contentHash: Int,
    val reviewedAt: Long = System.currentTimeMillis()
)
