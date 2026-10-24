package com.aburv.kurippidu.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A photo on a diary page. The image lives in files/attachments/[fileName]. */
@Entity(
    tableName = "attachments",
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
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val entryId: Int,
    val fileName: String,
    val createdAt: Long = System.currentTimeMillis()
)
