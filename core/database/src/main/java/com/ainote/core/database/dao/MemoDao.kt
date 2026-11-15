package com.ainote.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ainote.core.database.entity.MemoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {

    /** 未完成在前；同组内高优先级在前，再按截止时间与排序号 */
    @Query(
        """
        SELECT * FROM memos
        ORDER BY done ASC,
                 CASE priority WHEN 'HIGH' THEN 0 WHEN 'NORMAL' THEN 1 ELSE 2 END ASC,
                 (due_at IS NULL) ASC, due_at ASC, sort_order ASC, created_at DESC
        """
    )
    fun observeAll(): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos WHERE done = 0 ORDER BY sort_order ASC, created_at DESC")
    fun observeActive(): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getById(id: Long): MemoEntity?

    @Query("SELECT * FROM memos WHERE done = 0 AND due_at IS NOT NULL AND due_at <= :until")
    suspend fun getDueBefore(until: Long): List<MemoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memo: MemoEntity): Long

    @Update
    suspend fun update(memo: MemoEntity)

    @Delete
    suspend fun delete(memo: MemoEntity)

    @Query("UPDATE memos SET done = :done, done_at = :doneAt, updated_at = :now WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long?, now: Long)

    @Query("SELECT COUNT(*) FROM memos WHERE done = 0")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM memos WHERE done = 1 AND done_at BETWEEN :from AND :to")
    suspend fun countDoneInRange(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM memos WHERE created_at BETWEEN :from AND :to")
    suspend fun countCreatedInRange(from: Long, to: Long): Int
}
