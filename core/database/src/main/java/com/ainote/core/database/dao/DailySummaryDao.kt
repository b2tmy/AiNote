package com.ainote.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ainote.core.database.entity.DailySummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySummaryDao {

    @Query("SELECT * FROM daily_summaries ORDER BY summary_date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 60): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM daily_summaries WHERE summary_date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailySummaryEntity?

    @Query("SELECT * FROM daily_summaries ORDER BY summary_date DESC LIMIT 1")
    suspend fun getLatest(): DailySummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: DailySummaryEntity): Long

    @Query("DELETE FROM daily_summaries WHERE summary_date = :date")
    suspend fun deleteByDate(date: String)
}
