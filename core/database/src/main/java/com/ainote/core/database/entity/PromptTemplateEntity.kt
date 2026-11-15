package com.ainote.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 提示词模板：key 是场景标识（screenshot_analyze / note_summarize / daily_report …），
 * 用户可在设置页覆盖 systemPrompt 与 userTemplate。
 */
@Entity(
    tableName = "prompt_templates",
    indices = [Index(value = ["template_key"], unique = true)]
)
data class PromptTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "template_key")
    val templateKey: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String,

    /** 支持 {content} {date} {tags} 等占位符 */
    @ColumnInfo(name = "user_template")
    val userTemplate: String,

    @ColumnInfo(name = "version")
    val version: Int = 1,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
