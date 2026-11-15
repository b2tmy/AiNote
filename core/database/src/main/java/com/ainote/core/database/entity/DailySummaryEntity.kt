package com.ainote.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 每日汇报：一天一条，可重新生成 */
@Entity(
    tableName = "daily_summaries",
    indices = [Index(value = ["summary_date"], unique = true)]
)
data class DailySummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** yyyy-MM-dd（本地时区） */
    @ColumnInfo(name = "summary_date")
    val summaryDate: String,

    /** 汇报正文落成的笔记 */
    @ColumnInfo(name = "note_id")
    val noteId: Long? = null,

    /** 当日统计数据（截图条数、分类分布、待办完成率…）的 JSON */
    @ColumnInfo(name = "stats_json")
    val statsJson: String,

    /** AI 生成的叙述段落 */
    @ColumnInfo(name = "narrative")
    val narrative: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
