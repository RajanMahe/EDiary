package com.aburv.kurippidu.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** A #tag found in an entry's text (kept in step with the text on every save). */
@Entity(
    tableName = "entry_tags",
    primaryKeys = ["entryId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tag"])]
)
data class EntryTagEntity(
    val entryId: Int,
    val tag: String
)

data class TagCount(val tag: String, val count: Int)
