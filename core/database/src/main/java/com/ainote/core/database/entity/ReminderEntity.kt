package com.ainote.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ReminderRefType {
    NOTE,
    MEMO,
    EVENT
}

enum class ReminderRepeat {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

/** 统一提醒表：笔记 / 备忘 / 日程共用一套调度 */
@Entity(
    tableName = "reminders",
    indices = [
        Index("trigger_at"),
        Index("fired"),
        Index(value = ["ref_type", "ref_id"])
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "ref_type")
    val refType: ReminderRefType,

    @ColumnInfo(name = "ref_id")
    val refId: Long,

    @ColumnInfo(name = "trigger_at")
    val triggerAt: Long,

    @ColumnInfo(name = "repeat_rule")
    val repeatRule: ReminderRepeat = ReminderRepeat.NONE,

    @ColumnInfo(name = "fired")
    val fired: Boolean = false,

    @ColumnInfo(name = "fired_at")
    val firedAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
