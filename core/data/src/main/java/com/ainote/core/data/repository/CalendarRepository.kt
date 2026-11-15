package com.ainote.core.data.repository

import com.ainote.core.database.dao.CalendarEventDao
import com.ainote.core.database.dao.ReminderDao
import com.ainote.core.database.entity.CalendarEventEntity
import com.ainote.core.database.entity.EventSourceType
import com.ainote.core.database.entity.MemoEntity
import com.ainote.core.database.entity.ReminderEntity
import com.ainote.core.database.entity.ReminderRefType
import com.ainote.core.database.entity.ReminderRepeat
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日程与提醒的统一入口。
 *
 * 提醒不单独建「调度状态」——复用 reminders 表的 fired 字段，
 * 每次调度读库、每次触发写库，保证内存状态和持久化状态不会走偏。
 */
@Singleton
class CalendarRepository @Inject constructor(
    private val eventDao: CalendarEventDao,
    private val reminderDao: ReminderDao
) {

    fun observeInRange(from: Long, to: Long): Flow<List<CalendarEventEntity>> =
        eventDao.observeInRange(from, to)

    suspend fun getInRange(from: Long, to: Long): List<CalendarEventEntity> =
        eventDao.getInRange(from, to)

    suspend fun getById(id: Long): CalendarEventEntity? = eventDao.getById(id)

    /**
     * 新建日程。[reminderOffsetsMs] 是「提前多久提醒」的毫秒数，
     * 例如提前 10 分钟就传 10 * 60 * 1000。
     */
    suspend fun createEvent(
        title: String,
        startAt: Long,
        endAt: Long,
        allDay: Boolean = false,
        description: String = "",
        location: String? = null,
        color: Int? = null,
        reminderOffsetsMs: List<Long> = emptyList()
    ): Long {
        val now = System.currentTimeMillis()
        val id = eventDao.insert(
            CalendarEventEntity(
                title = title.trim(),
                description = description,
                startAt = startAt,
                endAt = if (endAt > startAt) endAt else startAt + DEFAULT_DURATION,
                allDay = allDay,
                location = location?.takeIf { it.isNotBlank() },
                color = color,
                sourceType = EventSourceType.LOCAL,
                createdAt = now,
                updatedAt = now
            )
        )
        replaceReminders(id, startAt, reminderOffsetsMs)
        return id
    }

    suspend fun updateEvent(
        event: CalendarEventEntity,
        reminderOffsetsMs: List<Long> = emptyList()
    ) {
        eventDao.update(event.copy(updatedAt = System.currentTimeMillis()))
        replaceReminders(event.id, event.startAt, reminderOffsetsMs)
    }

    suspend fun deleteEvent(event: CalendarEventEntity) {
        reminderDao.deleteByRef(ReminderRefType.EVENT, event.id)
        eventDao.delete(event)
    }

    /** 改时间后必须重建提醒，否则会按旧时间响 */
    private suspend fun replaceReminders(
        eventId: Long,
        startAt: Long,
        offsetsMs: List<Long>
    ) {
        reminderDao.deleteByRef(ReminderRefType.EVENT, eventId)
        val now = System.currentTimeMillis()
        offsetsMs.distinct().forEach { offset ->
            val triggerAt = startAt - offset
            if (triggerAt > now) {
                reminderDao.insert(
                    ReminderEntity(
                        refType = ReminderRefType.EVENT,
                        refId = eventId,
                        triggerAt = triggerAt,
                        repeatRule = ReminderRepeat.NONE,
                        createdAt = now
                    )
                )
            }
        }
    }

    // ---------------- 提醒 ----------------

    fun observePendingReminders(): Flow<List<ReminderEntity>> = reminderDao.observePending()

    suspend fun allPendingReminders(): List<ReminderEntity> = reminderDao.getAllPending()

    suspend fun reminderById(id: Long): ReminderEntity? = reminderDao.getById(id)

    suspend fun remindersOf(type: ReminderRefType, refId: Long): List<ReminderEntity> =
        reminderDao.getByRef(type, refId)

    suspend fun dueReminders(now: Long): List<ReminderEntity> = reminderDao.getDue(now)

    suspend fun markFired(id: Long) = reminderDao.markFired(id, System.currentTimeMillis())

    suspend fun reschedule(id: Long, nextTriggerAt: Long) =
        reminderDao.reschedule(id, nextTriggerAt)

    /** 稍后提醒：推后时间并把 fired 复位，保证重启后仍会被重排 */
    suspend fun snoozeReminder(id: Long, nextTriggerAt: Long) =
        reminderDao.snooze(id, nextTriggerAt)

    suspend fun addMemoReminder(memo: MemoEntity, triggerAt: Long): Long {
        reminderDao.deleteByRef(ReminderRefType.MEMO, memo.id)
        return reminderDao.insert(
            ReminderEntity(
                refType = ReminderRefType.MEMO,
                refId = memo.id,
                triggerAt = triggerAt,
                repeatRule = ReminderRepeat.NONE,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun cancelReminder(id: Long) = reminderDao.deleteById(id)

    /** 备忘完成 / 删除时撤掉它的提醒 */
    suspend fun cancelRemindersOf(type: ReminderRefType, refId: Long) =
        reminderDao.deleteByRef(type, refId)

    companion object {
        const val DEFAULT_DURATION = 60 * 60 * 1000L
    }
}
