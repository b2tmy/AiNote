package com.ainote.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT
}

@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("conversation_id"),
        Index("created_at")
    ]
)
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,

    @ColumnInfo(name = "role")
    val role: MessageRole,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "provider")
    val provider: String? = null,

    @ColumnInfo(name = "model")
    val model: String? = null,

    @ColumnInfo(name = "tokens_in")
    val tokensIn: Int? = null,

    @ColumnInfo(name = "tokens_out")
    val tokensOut: Int? = null,

    @ColumnInfo(name = "error")
    val error: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
