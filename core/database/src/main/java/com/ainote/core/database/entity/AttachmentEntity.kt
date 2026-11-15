package com.ainote.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("note_id")]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "note_id")
    val noteId: Long,

    /** content:// 或 app 私有目录 file:// */
    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    @ColumnInfo(name = "width")
    val width: Int = 0,

    @ColumnInfo(name = "height")
    val height: Int = 0,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
