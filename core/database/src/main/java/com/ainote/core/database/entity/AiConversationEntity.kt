package com.ainote.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 会话绑定的上下文对象 */
enum class ConversationScope {
    /** 针对某条截图记录 */
    SCREENSHOT,

    /** 针对某条笔记 */
    NOTE,

    /** 针对某条备忘 */
    MEMO,

    /** 无绑定，全局助手 */
    GLOBAL
}

@Entity(
    tableName = "ai_conversations",
    indices = [
        Index(value = ["scope_type", "scope_ref_id"]),
        Index("updated_at")
    ]
)
data class AiConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "scope_type")
    val scopeType: ConversationScope = ConversationScope.GLOBAL,

    @ColumnInfo(name = "scope_ref_id")
    val scopeRefId: Long? = null,

    @ColumnInfo(name = "title")
    val title: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
