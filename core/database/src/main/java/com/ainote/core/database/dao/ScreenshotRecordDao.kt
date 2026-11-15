package com.ainote.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ainote.core.database.entity.AnalyzeStatus
import com.ainote.core.database.entity.ScreenshotRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotRecordDao {

    @Query("SELECT * FROM screenshot_records ORDER BY captured_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ScreenshotRecordEntity>>

    @Query(
        """
        SELECT * FROM screenshot_records
        WHERE captured_at BETWEEN :from AND :to
        ORDER BY captured_at DESC
        """
    )
    fun observeInRange(from: Long, to: Long): Flow<List<ScreenshotRecordEntity>>

    @Query("SELECT * FROM screenshot_records WHERE status = :status ORDER BY captured_at ASC")
    suspend fun getByStatus(status: AnalyzeStatus): List<ScreenshotRecordEntity>

    @Query("SELECT * FROM screenshot_records WHERE id = :id")
    fun observeById(id: Long): Flow<ScreenshotRecordEntity?>

    @Query("SELECT * FROM screenshot_records WHERE id = :id")
    suspend fun getById(id: Long): ScreenshotRecordEntity?

    @Query("SELECT * FROM screenshot_records WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findBySha256(sha256: String): ScreenshotRecordEntity?

    /** 已处理过的指纹集合，用于批量去重 */
    @Query("SELECT sha256 FROM screenshot_records")
    suspend fun allHashes(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: ScreenshotRecordEntity): Long

    @Update
    suspend fun update(record: ScreenshotRecordEntity)

    @Query("UPDATE screenshot_records SET status = :status, error = NULL WHERE id = :id")
    suspend fun updateStatus(id: Long, status: AnalyzeStatus)

    @Query(
        """
        UPDATE screenshot_records
        SET status = :status, summary = :summary, tags_json = :tagsJson,
            todos_json = :todosJson, category = :category, note_id = :noteId,
            provider = :provider, model = :model, tokens_in = :tokensIn,
            tokens_out = :tokensOut, error = NULL
        WHERE id = :id
        """
    )
    suspend fun markDone(
        id: Long,
        status: AnalyzeStatus,
        summary: String?,
        tagsJson: String?,
        todosJson: String?,
        category: String?,
        noteId: Long?,
        provider: String?,
        model: String?,
        tokensIn: Int?,
        tokensOut: Int?
    )

    @Query("UPDATE screenshot_records SET status = :status, error = :error, retry_count = retry_count + 1 WHERE id = :id")
    suspend fun markFailed(id: Long, status: AnalyzeStatus, error: String?)

    @Query("UPDATE screenshot_records SET ocr_text = :text WHERE id = :id")
    suspend fun updateOcrText(id: Long, text: String?)

    @Query("SELECT COUNT(*) FROM screenshot_records WHERE captured_at BETWEEN :from AND :to")
    suspend fun countInRange(from: Long, to: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM screenshot_records
        WHERE captured_at BETWEEN :from AND :to AND status = 'DONE'
        """
    )
    suspend fun countDoneInRange(from: Long, to: Long): Int

    /** 当日已分析截图的分类分布（日报用） */
    @Query(
        """
        SELECT category, COUNT(*) AS count FROM screenshot_records
        WHERE captured_at BETWEEN :from AND :to AND status = 'DONE' AND category IS NOT NULL
        GROUP BY category ORDER BY count DESC
        """
    )
    suspend fun categoryCountsInRange(from: Long, to: Long): List<CategoryCount>

    /** 当日截图分析抽出的标签（日报里做高频统计） */
    @Query(
        """
        SELECT tags_json FROM screenshot_records
        WHERE captured_at BETWEEN :from AND :to AND status = 'DONE' AND tags_json IS NOT NULL
        """
    )
    suspend fun tagsJsonInRange(from: Long, to: Long): List<String>

    @Query("DELETE FROM screenshot_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM screenshot_records")
    suspend fun deleteAll()
}
