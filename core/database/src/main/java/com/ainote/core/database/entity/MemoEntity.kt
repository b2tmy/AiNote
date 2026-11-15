package com.ainote.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MemoPriority {
    LOW,
    NORMAL,
    HIGH
}

/** 轻量待办 / 备忘，一行一条 */
@Entity(
    tableName = "memos",
    indices = [
        Index("done"),
        Index("due_at"),
        Index("sort_order")
    ]
)
data class MemoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "done")
    val done: Boolean = false,

    @ColumnInfo(name = "done_at")
    val doneAt: Long? = null,

    @ColumnInfo(name = "priority")
    val priority: MemoPriority = MemoPriority.NORMAL,

    /** 截止时间 */
    @ColumnInfo(name = "due_at")
    val dueAt: Long? = null,

    /** 提醒时间（写入 reminders 表时同步） */
    @ColumnInfo(name = "remind_at")
    val remindAt: Long? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
