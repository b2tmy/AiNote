package com.ainote.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 笔记本类型：普通 / AI 截图分析 / 每日汇报 */
enum class NotebookType {
    NORMAL,
    AI_ANALYSIS,
    DAILY_REPORT
}

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: NotebookType = NotebookType.NORMAL,

    /** Material 图标名，null 表示用默认图标 */
    @ColumnInfo(name = "icon")
    val icon: String? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
