package com.ainote.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ainote.core.database.entity.CalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {

    /** 与给定区间有交集的事件 */
    @Query(
        """
        SELECT * FROM calendar_events
        WHERE start_at < :to AND end_at > :from
        ORDER BY start_at ASC
        """
    )
    fun observeInRange(from: Long, to: Long): Flow<List<CalendarEventEntity>>

    @Query(
        """
        SELECT * FROM calendar_events
        WHERE start_at < :to AND end_at > :from
        ORDER BY start_at ASC
        """
    )
    suspend fun getInRange(from: Long, to: Long): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun getById(id: Long): CalendarEventEntity?

    @Query("SELECT COUNT(*) FROM calendar_events WHERE start_at BETWEEN :from AND :to")
    suspend fun countInRange(from: Long, to: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CalendarEventEntity): Long

    @Update
    suspend fun update(event: CalendarEventEntity)

    @Delete
    suspend fun delete(event: CalendarEventEntity)
}
