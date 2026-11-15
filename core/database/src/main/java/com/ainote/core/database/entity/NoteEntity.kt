package com.ainote.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 笔记来源：手动 / 截图分析 / AI 生成 */
enum class NoteSourceType {
    MANUAL,
    SCREENSHOT,
    AI
}

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebook_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("notebook_id"),
        Index("updated_at"),
        Index("pinned")
    ]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "notebook_id")
    val notebookId: Long,

    @ColumnInfo(name = "title")
    val title: String = "",

    /** Markdown 原文 */
    @ColumnInfo(name = "content_md")
    val contentMd: String = "",

    /** 纯文本副本，供全文搜索 */
    @ColumnInfo(name = "content_text")
    val contentText: String = "",

    @ColumnInfo(name = "pinned")
    val pinned: Boolean = false,

    @ColumnInfo(name = "archived")
    val archived: Boolean = false,

    /** 软删除时间戳，null 表示未删除 */
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,

    @ColumnInfo(name = "source_type")
    val sourceType: NoteSourceType = NoteSourceType.MANUAL,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
