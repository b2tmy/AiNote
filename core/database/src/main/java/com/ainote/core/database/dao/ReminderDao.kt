package com.ainote.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ainote.core.database.entity.ReminderEntity
import com.ainote.core.database.entity.ReminderRefType
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE fired = 0 ORDER BY trigger_at ASC")
    fun observePending(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE fired = 0 AND trigger_at <= :now ORDER BY trigger_at ASC")
    suspend fun getDue(now: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE fired = 0")
    suspend fun getAllPending(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE ref_type = :type AND ref_id = :refId")
    suspend fun getByRef(type: ReminderRefType, refId: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("UPDATE reminders SET fired = 1, fired_at = :at WHERE id = :id")
    suspend fun markFired(id: Long, at: Long)

    /** 重复提醒触发后顺延到下一次 */
    @Query("UPDATE reminders SET trigger_at = :nextTrigger WHERE id = :id")
    suspend fun reschedule(id: Long, nextTrigger: Long)

    /**
     * 「稍后提醒」：推后时间的同时必须把 fired 复位，
     * 否则这条提醒不会再出现在「未触发」列表里，重启后不会被重排。
     */
    @Query("UPDATE reminders SET trigger_at = :nextTrigger, fired = 0, fired_at = NULL WHERE id = :id")
    suspend fun snooze(id: Long, nextTrigger: Long)

    @Query("DELETE FROM reminders WHERE ref_type = :type AND ref_id = :refId")
    suspend fun deleteByRef(type: ReminderRefType, refId: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}
