package com.ainote.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 事件来源：应用内自建 / 来自系统日历 */
enum class EventSourceType {
    LOCAL,
    SYSTEM
}

@Entity(
    tableName = "calendar_events",
    indices = [
        Index("start_at"),
        Index("external_event_id")
    ]
)
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "start_at")
    val startAt: Long,

    @ColumnInfo(name = "end_at")
    val endAt: Long,

    @ColumnInfo(name = "all_day")
    val allDay: Boolean = false,

    @ColumnInfo(name = "location")
    val location: String? = null,

    /** ARGB 颜色值 */
    @ColumnInfo(name = "color")
    val color: Int? = null,

    /** 重复规则（精简 RRULE，如 FREQ=WEEKLY;INTERVAL=1） */
    @ColumnInfo(name = "rrule")
    val rrule: String? = null,

    @ColumnInfo(name = "source_type")
    val sourceType: EventSourceType = EventSourceType.LOCAL,

    @ColumnInfo(name = "external_event_id")
    val externalEventId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
